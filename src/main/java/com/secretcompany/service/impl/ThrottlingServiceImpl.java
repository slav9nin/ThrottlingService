package com.secretcompany.service.impl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.secretcompany.dto.Sla;
import com.secretcompany.exception.NullableUserDataException;
import com.secretcompany.service.SlaService;
import com.secretcompany.service.ThrottlingService;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.secretcompany.service.ThrottlingConstants.AUTHORIZED_USER_ID;
import static com.secretcompany.service.ThrottlingConstants.UNAUTHORIZED_USER_ID;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;

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
 * 1. For particular UserId we don't have ongoing request to SlaService
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

    private final Map<String, List<String>> userToTokenListMap = new ConcurrentHashMap<>();
    private final Map<String, List<String>> authorizedUsersWithoutSlaToTokenMap = new ConcurrentHashMap<>();

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

//        final AtomicBoolean result = new AtomicBoolean(false);

        if (userToken.isPresent()) {
            //compute onl if we have Sla for token, otherwise return null. Null for authorized users which still does not have Sla
            UserData userData = tokenToUserDataMap.computeIfPresent(token, (t, ud) -> {
                if (ud.getSecondId() == secondFromEpoch) {
                    return new UserData(secondFromEpoch, ud.getSla(), ud.getRps() - 1, ud.getTokens(), ud.getSla().getUser());
                } else {
                    return new UserData(secondFromEpoch, ud.getSla(), ud.getSla().getRps(), ud.getTokens(), ud.getSla().getUser());
                }
            });
            //if null -> Authorized user without Sla yet.
            // Backoff -> compete those users for GuestRPS and attempt to SlaService if no one exists for particular token
            // todo when SlaService returns RealUserName we should remove token from DUMMY_KEY_FOR_AUTHORIZED_USERS!!!
            if (!Optional.ofNullable(userData).isPresent()) {
                //always not null
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

                //TODO submit request to SlaService if no one exists.
//                slaService.getSlaByToken(token);
                return checkRemainingRps(computedData) > 0;
            }


//            @NonNull UserData userData = tokenToUserDataMap.compute(token, (t, ud) -> {
//                if (Objects.nonNull(ud)) {
//                    //todo count rps through all tokens for particular user
//                    if (ud.getSecondId() == secondFromEpoch) {
//                        return new UserData(secondFromEpoch, ud.getSla(), ud.getRps() - 1, ud.getTokens(), ud.getSla().getUser());
//                    } else {
//                        return new UserData(secondFromEpoch, ud.getSla(), ud.getSla().getRps(), ud.getTokens(), ud.getSla().getUser());
//                    }
//                } else {
//                    //todo createGuestSla OR createAuthorizedSlaWithGuestRps???
////                    return createGuestSla(secondFromEpoch);
//                    //  further SlaRequests return Sla with Real userName and replace UUID with userName
//                    return authorizedDefaultSla(secondFromEpoch, token);
//                }
//            });
//            //todo count rps through all tokens for particular user
////            userData.getSla().getUser()
//
//            //todo do request to SlaService if no one exists for particular token!
//            // if already have ongoing request -> try to add callback on it with async prefix
//            CompletableFuture<Sla> ongoingSlaRequest =
//                    requestToSlaPerToken.computeIfAbsent(token, (t) -> slaService.getSlaByToken(token));


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

        return false;
    }

//    private void enqueueNewSlaRequest(final String token) {
//        //todo implement
//        slaService.getSlaByToken(token)
//                .thenApply()
//    }

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
        return new UserData(secondFromEpoch, authorizedDefaultSla, guestRps, tokens, authorizedDefaultSla.getUser());
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
    }
}
