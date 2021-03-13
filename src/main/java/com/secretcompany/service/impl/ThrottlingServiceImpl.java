package com.secretcompany.service.impl;

import com.google.common.annotations.VisibleForTesting;
import com.secretcompany.dto.Sla;
import com.secretcompany.exception.NullableUserDataException;
import com.secretcompany.service.SlaService;
import com.secretcompany.service.ThrottlingService;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.secretcompany.service.ThrottlingConstants.UNAUTHORIZED_USER_ID;

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

    private final int guestRps;
    private final SlaService slaService;
    private Clock systemClock;

    // TreeMap is red-black tree (search tree). We cannot use it due to unknown amount of users.
    // So it can lead to re-balancing of tree too many times. That's why using hash map.
    // Also, we want to use it as a CACHE. LRU cache (LinkedHashMap) is appropriate here.

    //todo avoid single lock. Replace with org.springframework.cache.concurrent.ConcurrentMapCache
//    private final Map<String, UserData> tokenToUserDataMap = Collections.synchronizedMap(createLRUCache(LRU_MAX_CACHE_CAPACITY));
    private final Map<String, UserData> tokenToUserDataMap = new ConcurrentHashMap<>();

    //todo avoid non-thread safe data structures.
    private final Map<String, CompletableFuture<Sla>> requestToSlaPerToken = new ConcurrentHashMap<>();

    public ThrottlingServiceImpl(int guestRps, SlaService slaService) {
        this.guestRps = guestRps;
        this.systemClock = Clock.systemUTC();
        this.slaService = slaService;

        //populate cache to avoid returning null
        tokenToUserDataMap.put(DUMMY_KEY, new UserData(getSecondFromEpoch(), createGuestSla(), guestRps));
    }

    //TODO replace synchronized with fine-grained lock implementation. Consider using concurrent structures, atomics.
    @Override
    public boolean isRequestAllowed(final String token) {
        //get current second from start of Epoch. It's an ID for current second.
        long secondFromEpoch = getSecondFromEpoch();

        //check current token is blank
        Optional<String> userToken = Optional.ofNullable(token)
                .filter(StringUtils::isNotBlank);

//        final AtomicBoolean result = new AtomicBoolean(false);

        if (userToken.isPresent()) {
            //critical section
            @NonNull UserData computed = tokenToUserDataMap.compute(token, (t, ud) -> {
                if (Objects.nonNull(ud)) {
                    if (ud.getSecondId() == secondFromEpoch) {
                        return new UserData(secondFromEpoch, ud.getSla(), ud.getRps() - 1);
                    } else {
                        return new UserData(secondFromEpoch, ud.getSla(), ud.getSla().getRps());
                    }
                } else {
                    //todo createGuestSla OR createAuthorizedSlaWithGuestRps???
//                    return createGuestSla(secondFromEpoch);
                    return authorizedDefaultSla(secondFromEpoch);
                }
            });



        } else {
            //Token is absent
            // Always should not be null.
            @NonNull UserData newUserData = tokenToUserDataMap.computeIfPresent(DUMMY_KEY, (k, v) -> {
                long secondId = v.getSecondId();
                if (secondId == secondFromEpoch) {
                    // within same second
                    return new UserData(secondFromEpoch, v.getSla(), v.getRps() - 1);
                } else {
                    // new second
                    return createGuestSla(secondFromEpoch);
                }
            });

            return checkRemainingRps(newUserData) > 0;
        }

        return false;
    }

    private void enqueueNewSlaRequest(final String token) {
        //todo implement
    }

    private long checkRemainingRps(UserData newUserData) {
        return Optional.ofNullable(newUserData)
                .map(UserData::getRps)
                .orElseThrow(NullableUserDataException::new);

    }

    private UserData createGuestSla(long secondFromEpoch) {
        Sla guestSla = createGuestSla();
        return new UserData(secondFromEpoch, guestSla, guestRps);
    }

    private UserData authorizedDefaultSla(long secondFromEpoch) {
        Sla authorizedDefaultSla = createAuthorizedDefaultSla();
        return new UserData(secondFromEpoch, authorizedDefaultSla, guestRps);
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
        return new Sla(UUID.randomUUID().toString(), guestRps);
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

        public UserData(long secondId, Sla sla, int rps) {
            this.secondId = secondId;
            this.sla = sla;
            this.rps = rps;
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
    }
}
