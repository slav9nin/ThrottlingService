package com.secretcompany.service.impl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.secretcompany.dto.Sla;
import com.secretcompany.exception.MultipleValuesUserDataException;
import com.secretcompany.exception.NullableUserDataException;
import com.secretcompany.service.SlaService;
import com.secretcompany.service.ThrottlingService;
import lombok.NonNull;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.secretcompany.config.ThrottlingConfiguration.CUSTOM_FORK_JOIN_POOL;
import static com.secretcompany.service.ThrottlingConstants.AUTHORIZED_USER_ID;
import static com.secretcompany.service.ThrottlingConstants.UNAUTHORIZED_USER_ID;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toSet;

/**
 * ThrottlingServiceImpl Rules:
 * 1. Token is absent -> GuestRPS
 * 2. SlaService haven't response Sla yet -> GuestRPS
 * 3. RPS per user, not per token. Each user can have several tokens
 * 4. ThrottlingService should respond during 5 ms.
 * 5. SlaService does it within 300 ms.
 * 6. Throttling Service should not wait for Sla. Cache result from Sla. Support already issued Slas.
 *
 * We make a request to SlaService IFF:
 * 1. For particular Token we don't have ongoing request to SlaService.
 * 2. On each request to ThrottlingService if it satisfies the previous statement.
 */
public class ThrottlingServiceImpl implements ThrottlingService {
    private static final String DUMMY_KEY = UUID.randomUUID().toString();
    private static final String DUMMY_KEY_FOR_AUTHORIZED_USERS = UUID.randomUUID().toString();

    private final int guestRps;
    private final SlaService slaService;
    private Clock systemClock;

    // TreeMap is red-black tree (search tree). We cannot use it due to unknown amount of users.
    // So it can lead to re-balancing of tree too many times. That's why using hash map.
    // Also, we want to use it as a CACHE. LRU cache (LinkedHashMap) is appropriate here.

    //todo avoid single lock. Replace with org.springframework.cache.concurrent.ConcurrentMapCache
//    private final Map<String, UserData> tokenToUserDataMap = Collections.synchronizedMap(createLRUCache(LRU_MAX_CACHE_CAPACITY));
    private final Map<String, UserData> tokenToUserDataMap = new ConcurrentHashMap<>();

    //to support RPS by UserName
    private final Map<String, Set<UserData>> userToUserDataSetMap = new ConcurrentHashMap<>();

    //todo avoid non-thread safe data structures.
    private final Map<String, CompletableFuture<Sla>> requestToSlaPerToken = new ConcurrentHashMap<>();

    public ThrottlingServiceImpl(int guestRps, SlaService slaService) {
        this.guestRps = guestRps;
        this.systemClock = Clock.systemUTC();
        this.slaService = slaService;

        //populate cache to avoid returning null
        tokenToUserDataMap.put(DUMMY_KEY, new UserData(getSecondFromEpoch(), createGuestSla(), guestRps, emptySet(), UNAUTHORIZED_USER_ID));
        tokenToUserDataMap.put(DUMMY_KEY_FOR_AUTHORIZED_USERS, new UserData(getSecondFromEpoch(), createAuthorizedDefaultSla(), guestRps, emptySet(), AUTHORIZED_USER_ID));
    }

    @Override
    public boolean isRequestAllowed(final String token) {
        //get current second from start of Epoch. It's an ID for current second.
        long secondFromEpoch = getSecondFromEpoch();

        //check current token is blank
        Optional<String> userToken = Optional.ofNullable(token)
                .filter(StringUtils::isNotBlank);

        if (userToken.isPresent()) {
            //compute onl if we have Sla for token, otherwise return null. Null for authorized users which still does not have Sla
            UserData userData = tokenToUserDataMap.computeIfPresent(token, (t, ud) -> {
                if (ud.getSecondId() == secondFromEpoch) {
                    return new UserData(secondFromEpoch, ud.getSla(), ud.getRps() - 1, ud.getTokens(), ud.getSla().getUser());
                } else {
                    return new UserData(secondFromEpoch, ud.getSla(), ud.getSla().getRps(), ud.getTokens(), ud.getSla().getUser());
                }
            });

            //do request to SlaService if no one exists for the same token.
            requestToSlaPerToken.computeIfAbsent(token, (t) -> slaService.getSlaByToken(token))
                    .thenAcceptAsync(sla -> {
                        //when SlaService returns RealUserName we should
                        //  1. add entry to tokenToUserDataMap and then
                        //  2. remove token from DUMMY_KEY_FOR_AUTHORIZED_USERS!!!
                        //  Order matters!
                        //  3. Cleanup requestToSlaPerToken to avoid memory leak.

                        // here we update tokenToUserDataMap twice without any locking, but it's ok, due to order and key which we are using!

                        //1. add to tokenToUserDataMap Sla
                        tokenToUserDataMap.compute(token, (t, ud) -> {
                            if (Objects.nonNull(ud)) {
                                // existing entry for this token.
                                // should update token and support existing values of ud (currently computed UserData)
                                // also, we support remaining RPS even if New SLA has new one. To simplify solution.
                                return new UserData(ud.getSecondId(), sla, ud.getRps(), ud.getTokens(), sla.getUser());

                            } else {
                                //no entry yet for this token
                                //create new UserData with secondFromEpoch
                                return new UserData(secondFromEpoch, sla, sla.getRps(), ImmutableSet.of(token), sla.getUser());
                            }
                        });

                        //2. remove from tokenToUserDataMap by DUMMY_KEY_FOR_AUTHORIZED_USERS key this token from set of tokens
                        tokenToUserDataMap.computeIfPresent(DUMMY_KEY_FOR_AUTHORIZED_USERS, (k, v) -> {
                            Set<String> tokens = v.getTokens().stream()
                                    .filter(t -> !Objects.equals(token, t))
                                    .collect(toSet());
                            return new UserData(v.getSecondId(), v.getSla(), v.getRps(), ImmutableSet.copyOf(tokens), v.getUserId());
                        });

                    }, CUSTOM_FORK_JOIN_POOL)
                    // 3. cleanup request pool after completion to avoid memory leak.
                    .thenRunAsync(() -> requestToSlaPerToken.remove(token), CUSTOM_FORK_JOIN_POOL);

            if (Optional.ofNullable(userData).isPresent()) {
                //retrieve user from Sla and then retrieve all entries by UserId.
                @NonNull Set<UserData> userTokenData = userToUserDataSetMap.compute(userData.getSla().getUser(), (k, v) -> {
                    if (v == null || IterableUtils.isEmpty(v)) {
                        Sla sla = userData.getSla();
                        String user = sla.getUser();
                        long secondId = userData.getSecondId();
                        int rps = userData.getRps();
                        Set<String> tokens = new HashSet<>(userData.getTokens());
                        tokens.add(token);
                        ImmutableSet<String> newTokenSet = ImmutableSet.copyOf(tokens);
                        UserData data = new UserData(secondId, sla, rps, newTokenSet, user);
                        return ImmutableSet.of(data);
                    } else {
                        // already have Set<UserData
                        //userData can already by in userToUserDataSetMap. We need to check it.
                        Sla sla = userData.getSla();
                        String user = sla.getUser();
                        long secondId = userData.getSecondId();
                        int rps = userData.getRps();
                        Set<String> tokens = new HashSet<>(userData.getTokens());
                        tokens.add(token);
                        ImmutableSet<String> newTokenSet = ImmutableSet.copyOf(tokens);
                        UserData data = new UserData(secondId, sla, rps, newTokenSet, user);
                        //put if absent. Based on equals/hashCode implementation. Should not put if already haas UserData with the same token.
                        HashSet<UserData> existingSet = new HashSet<>(v);
                        existingSet.add(data);
                        return ImmutableSet.copyOf(existingSet);
                    }
                });
                //iterate through userTokenData and count RPS by UserName!
                Map<Sla, Set<UserData>> collect = userTokenData.stream()
                        .collect(Collectors.groupingBy(UserData::getSla, Collectors.mapping(Function.identity(), toSet())));
                if (collect.size() > 1) {
                    throw new MultipleValuesUserDataException();
                }

                Set<UserData> userDataSet = collect.get(userData.getSla());

                //each token of the same user has own RPS
                final int existingRpsThroughAllTokenRps = userDataSet.stream()
                        .mapToInt(UserData::getRps)
                        .sum();

                //Sla RPS
                int rps = userData.getSla().getRps();
                //count of existing tokens per user
                int size = userDataSet.size();
                //max Sla RPS
                final int maxRpsByUser = userData.getSla().getRps();

                int total = maxRpsByUser * size;

                return total - existingRpsThroughAllTokenRps < rps;

            } else {
                // userData == null
                // computedData always is not null
                @NonNull UserData computedData = tokenToUserDataMap.computeIfPresent(DUMMY_KEY_FOR_AUTHORIZED_USERS, (k, v) -> {
                    long secondId = v.getSecondId();
                    if (secondId == secondFromEpoch) {
                        // within same second
                        Set<String> tokens = new HashSet<>(v.getTokens());
                        tokens.add(token);
                        Set<String> collectedTokens = ImmutableSet.copyOf(tokens);
                        int existingRps = v.getRps();
                        return new UserData(secondFromEpoch, v.getSla(), existingRps - 1, collectedTokens, v.getSla().getUser());
                    } else {
                        // new second
                        Set<String> tokens = new HashSet<>(v.getTokens());
                        tokens.add(token);
                        Set<String> collectedTokens = ImmutableSet.copyOf(tokens);
                        int rps = v.getSla().getRps();
                        return authorizedDefaultSla(secondFromEpoch, rps, collectedTokens);
                    }
                });
                //all authorized but without Sla users should compete between each other. Default RPS == GuestRPS
                return checkRemainingRps(computedData) > 0;
            }

        } else {
            //Token is absent. All unauthorized users compete for GuestRPS.
            // UserData always should not be null.
            @NonNull UserData newUserData = tokenToUserDataMap.computeIfPresent(DUMMY_KEY, (k, v) -> {
                long secondId = v.getSecondId();
                if (secondId == secondFromEpoch) {
                    // within same second
                    int existingRps = v.getRps();
                    return new UserData(secondFromEpoch, v.getSla(), existingRps - 1, emptySet(), v.getSla().getUser());
                } else {
                    // new second
                    return createGuestSla(secondFromEpoch);
                }
            });

            return checkRemainingRps(newUserData) > 0;
        }
    }

    private long checkRemainingRps(@NonNull UserData newUserData) {
        return Optional.ofNullable(newUserData)
                .map(UserData::getRps)
                .orElseThrow(NullableUserDataException::new);
    }

    private UserData createGuestSla(long secondFromEpoch) {
        Sla guestSla = createGuestSla();
        return new UserData(secondFromEpoch, guestSla, guestRps, emptySet(), guestSla.getUser());
    }

    private UserData authorizedDefaultSla(long secondFromEpoch, String token) {
        Sla authorizedDefaultSla = createAuthorizedDefaultSla();
        return new UserData(secondFromEpoch, authorizedDefaultSla, guestRps, singleton(token), authorizedDefaultSla.getUser());
    }

    private UserData authorizedDefaultSla(long secondFromEpoch, int rps, Set<String> tokens) {
        Sla authorizedDefaultSla = createAuthorizedDefaultSla();
        return new UserData(secondFromEpoch, authorizedDefaultSla, rps, tokens, authorizedDefaultSla.getUser());
    }

    /**
     * @return number of second from epoch.
     * See Instant.getEpochSecond java-doc.
     * Use this always increase sequence as Id of current second.
     */
    private long getSecondFromEpoch() {
        return Instant.now(systemClock).getEpochSecond();
    }

    private Sla createGuestSla() {
        return new Sla(UNAUTHORIZED_USER_ID, guestRps);
    }

    /**
     * Authorized users should not compete with non-authorized. Each authorized user w/o Sla from SlaService
     * has each own default GuestRPS.
     * @return Sla for authorized user with random UUID and GuestRPS to prevent competing with non-authorized users.
     */
    private Sla createAuthorizedDefaultSla() {
//        return new Sla(UUID.randomUUID().toString(), guestRps);
        return new Sla(AUTHORIZED_USER_ID, guestRps);
    }

    @VisibleForTesting
    <K, V> LinkedHashMap<K, V> createLRUCache(int maxCapacity) {
        return new LinkedHashMap<>(maxCapacity, 0.75f, true);
    }

    @VisibleForTesting
    void setSystemClock(Clock systemClock) {
        this.systemClock = systemClock;
    }

    /**
     * Immutable. Sla is also immutable
     */
    private static class UserData {
        private final long secondId;
        private final Sla sla;
        private final int rps;
        private final Set<String> tokens;
        private final String userId;

        public UserData(long secondId, Sla sla, int rps, Set<String> tokens, String userId) {
            this.secondId = secondId;
            this.sla = sla;
            this.rps = rps;
            this.tokens = tokens;
            this.userId = userId;
        }

        public long getSecondId() {
            return secondId;
        }

        public Sla getSla() {
            return sla;
        }

        public int getRps() {
            return rps;
        }

        public Set<String> getTokens() {
            return new HashSet<>(tokens);
        }

        public String getUserId() {
            return userId;
        }

        //compare all fields. Even Set<Token>
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UserData userData = (UserData) o;
            return secondId == userData.secondId && rps == userData.rps && Objects.equals(sla, userData.sla)
                    && Objects.equals(tokens, userData.tokens) && Objects.equals(userId, userData.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(secondId, sla, rps, tokens, userId);
        }
    }
}
