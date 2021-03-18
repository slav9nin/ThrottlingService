package com.secretcompany.service.impl;

import com.google.common.annotations.VisibleForTesting;
import com.secretcompany.dto.Sla;
import com.secretcompany.dto.TimeWindow;
import com.secretcompany.service.SlaService;
import com.secretcompany.service.ThrottlingService;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.secretcompany.config.ThrottlingConfiguration.CUSTOM_FORK_JOIN_POOL;

/**
 * 1. No token -> UnAuthorized Users. All compete for UnAuthorized GuestRPS
 * 2. Token, No Sla -> Authorized Users w/o SLA. All these users compete for Authorized GuestRPS.
 * 3. Token and Sla -> Welcome on board. Each user has own RPS.
 *
 * Authorized user can have several tokens.
 */
public class WindowThrottlingServiceImpl implements ThrottlingService {
    private static final String UNAUTHORIZED_USERS = UUID.randomUUID().toString();
    private static final String AUTHORIZED_USERS_WITHOUT_SLA = UUID.randomUUID().toString();

    private final int guestRps;
    private final SlaService slaService;
    private final Map<String, TimeWindow> userToTimeWindowMap = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Sla>> requestToSlaPerToken = new ConcurrentHashMap<>();
    private final Map<String, Sla> tokenSlaMap = new ConcurrentHashMap<>();

    private Clock systemClock;

    public WindowThrottlingServiceImpl(final int guestRps, final SlaService slaService) {
        this.guestRps = guestRps;
        this.slaService = slaService;
        this.systemClock = Clock.systemUTC();
    }

    @Override
    public boolean isRequestAllowed(String token) {
        //get current millis
        final Instant now = Instant.now(systemClock);
        final long current = now.toEpochMilli();
        //determine the end of the timed window
        final long end = now.plusMillis(1000L).toEpochMilli();

        // check current token is blank
        Optional<String> userToken = Optional.ofNullable(token)
                .filter(StringUtils::isNotBlank);

        if (userToken.isPresent()) {

            //request to SlaService
            checkSlaService(token);

            //retrieve Sla
            Sla sla = tokenSlaMap.get(token);

            if (Objects.nonNull(sla)) {
                //Check particular user's Sla
                return checkRequestIsAllowed(current, end, sla.getUser(), sla.getRps());
            } else {
                //Sla hasn't arrived yet. Compete for default RPS
                return checkRequestIsAllowed(current, end, AUTHORIZED_USERS_WITHOUT_SLA, guestRps);
            }
        } else {
            // Token is absent. All unauthorized users compete for GuestRPS.
            return checkRequestIsAllowed(current, end, UNAUTHORIZED_USERS, guestRps);
        }
    }

    private boolean checkRequestIsAllowed(long current, long end, String token, long rps) {
        @NonNull TimeWindow currentWindow = userToTimeWindowMap.compute(token, computeTimedWindow(current, end, rps));
        return currentWindow.getRps() >= 0;
    }

    private BiFunction<String, TimeWindow, TimeWindow> computeTimedWindow(long current, long end, long rps) {
        return (userId, window) -> {
            if (Objects.nonNull(window)) {
                //window is already present.
                //check do we still in this window.(current time within start-end of window). If yes -> decrement RPS
                if (current < window.getEndMillis()) {
                    //here we should support existing RPS. If Sla comes with new RPS we will support it in the next window
                    return new TimeWindow(window.getStartMillis(), window.getEndMillis(), window.getRps() - 1);
                } else {
                    //if not - means we start new window
                    return new TimeWindow(current, end, rps - 1);
                }

            } else {
                //window isn't created yet. create a new one and decrement default RPS
                return new TimeWindow(current, end, rps - 1);
            }
        };
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

                    //1. add to tokenSlaMap Sla
                    tokenSlaMap.put(token, sla);

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

    @VisibleForTesting
    void setSystemClock(Clock systemClock) {
        this.systemClock = systemClock;
    }

    @VisibleForTesting
    Map<String, CompletableFuture<Sla>> getRequestToSlaPerToken() {
        return requestToSlaPerToken;
    }
}
