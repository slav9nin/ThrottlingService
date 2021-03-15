package com.secretcompany.service.impl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.secretcompany.dto.Sla;
import com.secretcompany.exception.MultipleValuesUserDataException;
import com.secretcompany.exception.NullableUserDataException;
import com.secretcompany.service.SlaService;
import com.secretcompany.service.ThrottlingService;
import lombok.NonNull;
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

    // TODO LinkedHashMap can be used as LRU Cache. But we don't have a concurrent version. Consider using org.springframework.cache.concurrent.ConcurrentMapCache
    private final Map<String, UserData> tokenToUserDataMap = new ConcurrentHashMap<>();
//    private final Map<String, UserData> tokenToUserDataMap = createLRUCache(LRU_MAX_CACHE_CAPACITY);

    //to support RPS by UserName
    private final Map<String, UserTokenInfo> userToUserDataMap = new ConcurrentHashMap<>();

    private final Map<String, CompletableFuture<Sla>> requestToSlaPerToken = new ConcurrentHashMap<>();

    public ThrottlingServiceImpl(int guestRps, SlaService slaService) {
        this.guestRps = guestRps;
        this.systemClock = Clock.systemUTC();
        this.slaService = slaService;

        //populate cache to avoid returning null
        tokenToUserDataMap.put(DUMMY_KEY, new UserData(getSecondFromEpoch(), createGuestSla(), guestRps, DUMMY_KEY));
        tokenToUserDataMap.put(DUMMY_KEY_FOR_AUTHORIZED_USERS, new UserData(getSecondFromEpoch(), createAuthorizedDefaultSla(), guestRps, DUMMY_KEY_FOR_AUTHORIZED_USERS));
    }

    @Override
    public boolean isRequestAllowed(final String token) {
        // get current second from start of Epoch. It's an ID for current second.
        long secondFromEpoch = getSecondFromEpoch();

        // check current token is blank
        Optional<String> userToken = Optional.ofNullable(token)
                .filter(StringUtils::isNotBlank);

        if (userToken.isPresent()) {
            //compute only if we have Sla for token, otherwise return null. Null for authorized users which still does not have Sla

            UserData userData = tokenToUserDataMap.get(token);

            // do request to SlaService if no one exists for the same token.
            checkSlaService(token);

            if (Optional.ofNullable(userData).isPresent()) {
                // retrieve user from Sla and then retrieve all entries by UserId.
                @NonNull UserTokenInfo userTokenInfo = userToUserDataMap.compute(userData.getSla().getUser(), (k, v) -> {
                    if (Objects.isNull(v)) {
                        return new UserTokenInfo(userData.getSla().getUser(), ImmutableSet.of(token));
                    } else {
                        if (v.getTokens().stream().anyMatch(t -> Objects.equals(t, token))) {
                            //the same token
                            return new UserTokenInfo(userData.getSla().getUser(), v.getTokens());
                        }
                        return new UserTokenInfo(userData.getSla().getUser(), ImmutableSet.<String>builder().addAll(v.getTokens()).add(token).build());
                    }
                });
                final Set<UserData> allTokensByUser = new HashSet<>();
                for (String usersToken: userTokenInfo.getTokens()) {
                    if (Objects.equals(usersToken, token)) {
                        //compute rps only for current token.
                        @NonNull UserData slaData = tokenToUserDataMap.computeIfPresent(usersToken, (t, ud) -> {
                            if (ud.getSecondId() == secondFromEpoch) {
                                return new UserData(ud.getSecondId(), ud.getSla(), ud.getRps() - 1, ud.getToken());
                            } else {
                                return new UserData(secondFromEpoch, ud.getSla(), ud.getSla().getRps() - 1, ud.getToken());
                            }
                        });
                        allTokensByUser.add(slaData);
                    } else {
                        @NonNull UserData slaData = tokenToUserDataMap.get(usersToken);
                        allTokensByUser.add(slaData);
                    }
                }
                Map<Sla, Set<UserData>> collect = allTokensByUser.stream()
                        .filter(ud -> Objects.equals(ud.getSecondId(), secondFromEpoch))
                        .collect(Collectors.groupingBy(UserData::getSla, Collectors.mapping(Function.identity(), toSet())));
                if (collect.size() > 1) {
                    throw new MultipleValuesUserDataException();
                }

                // each token of the same user has own RPS
                final long existingRpsThroughAllTokenRps = allTokensByUser.stream()
                        .mapToLong(UserData::getRps)
                        .sum();

                //count of existing tokens per user
                int size = allTokensByUser.size();
                //max Sla RPS
                final long maxRpsByUser = userData.getSla().getRps();

                long total = maxRpsByUser * size;

                return total - existingRpsThroughAllTokenRps <= maxRpsByUser;
            } else {
                // userData == null
                // computedData always is not null
                @NonNull UserData computedData = tokenToUserDataMap.computeIfPresent(DUMMY_KEY_FOR_AUTHORIZED_USERS, (k, v) -> computeUserData(secondFromEpoch, v));
                // all authorized but without Sla users should compete between each other. Default RPS == GuestRPS
                return checkRemainingRps(computedData) >= 0;
            }

        } else {
            // Token is absent. All unauthorized users compete for GuestRPS.
            // UserData always should not be null.
            @NonNull UserData newUserData = tokenToUserDataMap.computeIfPresent(DUMMY_KEY, (k, v) -> computeUserData(secondFromEpoch, v));

            return checkRemainingRps(newUserData) >= 0;
        }
    }

    private UserData computeUserData(long secondFromEpoch, UserData v) {
        long secondId = v.getSecondId();
        if (secondId == secondFromEpoch) {
            // within same second
            return new UserData(secondId, v.getSla(), v.getRps() - 1, v.getToken());
        } else {
            // new second
            return new UserData(secondFromEpoch, v.getSla(), v.getSla().getRps() - 1, v.getToken());
        }
    }

    private void checkSlaService(String token) {
        requestToSlaPerToken.computeIfAbsent(token, proceedSlaService(token))
                .thenAcceptAsync(sla -> {
                    if (Objects.isNull(sla)) {
                        //Sla Service does not have any mappings for this token
                        return;
                    }
                    //when SlaService returns RealUserName we should
                    //  1. add entry to tokenToUserDataMap and then
                    //  2. Cleanup requestToSlaPerToken to avoid memory leak.

                    //1. add to tokenToUserDataMap Sla
                    tokenToUserDataMap.compute(token, (t, ud) -> {
                        if (Objects.nonNull(ud)) {
                            // existing entry for this token.
                            // should update token and support existing values of ud (currently computed UserData)
                            // also, we support remaining RPS even if New SLA has new one. To simplify solution.
                            return new UserData(ud.getSecondId(), sla, ud.getRps(), ud.getToken());

                        } else {
                            //no entry yet for this token
                            //create new UserData with secondFromEpoch
                            return new UserData(getSecondFromEpoch(), sla, sla.getRps(), token);
                        }
                    });

                }, CUSTOM_FORK_JOIN_POOL)
                // 2. cleanup request pool after completion to avoid memory leak.
                .thenRunAsync(() -> requestToSlaPerToken.remove(token), CUSTOM_FORK_JOIN_POOL);
    }

    private Function<String, CompletableFuture<Sla>> proceedSlaService(String token) {
        //always run on separate thread pool. We aru using custom FORK_JOIN_POOL to avoid problem with default one in Java Stream API
        return (t) ->
                CompletableFuture.completedFuture(0)
                        .thenComposeAsync((v) -> slaService.getSlaByToken(token), CUSTOM_FORK_JOIN_POOL);
    }

    private long checkRemainingRps(@NonNull UserData newUserData) {
        return Optional.ofNullable(newUserData)
                .map(UserData::getRps)
                .orElseThrow(NullableUserDataException::new);
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
     * Authorized users should not compete with non-authorized.
     * @return Sla for authorized user with AUTHORIZED_USER_ID Id and GuestRPS to prevent competing with non-authorized users.
     */
    private Sla createAuthorizedDefaultSla() {
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

    @VisibleForTesting
    Map<String, UserData> getTokenToUserDataMap() {
        return tokenToUserDataMap;
    }

    @VisibleForTesting
    Map<String, UserTokenInfo> getUserToUserDataSetMap() {
        return userToUserDataMap;
    }

    @VisibleForTesting
    Map<String, CompletableFuture<Sla>> getRequestToSlaPerToken() {
        return requestToSlaPerToken;
    }

    /**
     * Immutable.
     */
    static class UserData {
        private final long secondId;
        private final Sla sla;
        private final long rps;
        private final String token;

        public UserData(long secondId, Sla sla, long rps, String token) {
            Objects.requireNonNull(sla, "Sla should not be null");
            Objects.requireNonNull(token, "token Set should not be null");
            this.secondId = secondId;
            this.sla = sla;
            this.rps = rps;
            this.token = token;
        }

        public long getSecondId() {
            return secondId;
        }

        public Sla getSla() {
            return sla;
        }

        public long getRps() {
            return rps;
        }

        public String getToken() {
            return token;
        }

        //except Sla and UserId
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UserData userData = (UserData) o;
            return secondId == userData.secondId && rps == userData.rps && Objects.equals(token, userData.token);
        }

        @Override
        public int hashCode() {
            return Objects.hash(secondId, rps, token);
        }
    }

    static class UserTokenInfo {
        private final String userName;
        private final Set<String> tokens;

        UserTokenInfo(String userName, Set<String> tokens) {
            Objects.requireNonNull(userName, "UserName is required");
            Objects.requireNonNull(tokens, "tokenSet is required");
            this.userName = userName;
            this.tokens = tokens;
        }

        public String getUserName() {
            return userName;
        }

        public Set<String> getTokens() {
            return tokens;
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UserTokenInfo that = (UserTokenInfo) o;
            return Objects.equals(userName, that.userName) && Objects.equals(tokens, that.tokens);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userName, tokens);
        }
    }
}
