package com.secretcompany.service.impl;

import com.google.common.annotations.VisibleForTesting;
import com.secretcompany.dto.Sla;
import com.secretcompany.service.ThrottlingService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.util.Optionals;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.secretcompany.service.ThrottlingConstants.LRU_MAX_CACHE_CAPACITY;
import static com.secretcompany.service.ThrottlingConstants.USER_ID_IS_REQUIRED;

/**
 * ThrottlingServiceImpl Rules:
 * 1. Token is absent -> GuestRPS
 * 2. SlaService haven't response Sla yet -> GuestRPS
 * 3. RPS per user, not per token. Each user can have several tokens
 *
 * Additional Questions: If SlaService still unavailable OR we do not know mapping between token and user due to token is null
 * How we can support throttling for this case? Possible answer: token should contain UserId. always... ?
 *
 * 4. ThrottlingService should respond during 5 ms.
 * 5. SlaService does it within 300 ms.
 * 6. Throttling Service should not wait for Sla. Cache result from Sla. Support already issued Slas.
 *
 * ThrottlingServiceImpl responsibility:
 * Per token determine user and remaining RPS
 * Question: if token is null or blank what we should do in that case?
 */
public class ThrottlingServiceImpl implements ThrottlingService {
    private final int guestRps;

    // TreeMap is red-black tree (search tree). We cannot use it due to unknown amount of users.
    // So it can lead to re-balancing of tree too many times. That's why using hash map.
    // Also, we want to use it as a CACHE. LRU cache (LinkedHashMap) is appropriate here.
    private final Map<String, Pair<String,Sla>> tokenToUserSlaMap = createLRUCache(LRU_MAX_CACHE_CAPACITY);
//    private final Map<String, String> tokenToUserMap = createLRUCache(LRU_MAX_CACHE_CAPACITY);

    public ThrottlingServiceImpl(int guestRps) {
        this.guestRps = guestRps;
    }

    @Override
    public synchronized boolean isRequestAllowed(final String token, final String userId) {
        Objects.requireNonNull(userId, USER_ID_IS_REQUIRED);
        Optional<String> userToken = Optional.ofNullable(token)
                .filter(StringUtils::isNotBlank);

        Optionals.ifPresentOrElse(userToken, this::retrieveSla, this::generateNewRecord);

        //todo
        return false;
    }

    private Sla retrieveSla(final String token) {
        //todo
        throw new UnsupportedOperationException();
//        tokenToUserSlaMap.computeIfAbsent(token, (t, s) -> {
//            if (StringUtils.isBlank(t)) {
//                return new Sla(UUID.randomUUID().toString(), guestRps);
//            }
//        });
    }

    private void generateNewRecord() {
        //TODO in case token is blank
    }

    @VisibleForTesting
    <K, V> LinkedHashMap<K, V> createLRUCache(int maxCapacity) {
        return new LinkedHashMap<>(maxCapacity, 0.75f, true);
    }
}
