package com.secretcompany.service.impl;

import com.secretcompany.mock.EmptySlaService;
import com.secretcompany.mock.SlaServiceStubWithDelay;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.secretcompany.mock.SlaServiceStubConstants.TOKEN_1_1;
import static com.secretcompany.mock.SlaServiceStubConstants.TOKEN_1_2;
import static com.secretcompany.mock.SlaServiceStubConstants.TOKEN_2_1;
import static com.secretcompany.mock.SlaServiceStubConstants.TOKEN_2_2;
import static com.secretcompany.mock.SlaServiceStubConstants.USER_1_MAX_RPS;
import static com.secretcompany.mock.SlaServiceStubConstants.USER_1_SLA;
import static com.secretcompany.mock.SlaServiceStubConstants.USER_2_MAX_RPS;
import static com.secretcompany.mock.SlaServiceStubConstants.USER_2_SLA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class ThrottlingServiceTest {

    private static final int GUEST_RPS = 20;
    private static final int REAL_RPS = 2500;
    private static final long SLA_DELAY = 1000L * 1000 * 1000 * 3; //3 seconds
    private static final long GUARDED_PERIOD = 1000L * 1000 * 1000; //1 second

    //Using Implementation instead of Interface to get access to package-private(VisibleForTesting) methods!
    private ThrottlingServiceImpl throttlingService ;

    //Test should return always the same second from epoch.
    private Clock fixedClock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

    @Before
    public void init() {
        throttlingService = new ThrottlingServiceImpl(GUEST_RPS, new SlaServiceStubWithDelay(SLA_DELAY, false));
        throttlingService.setSystemClock(fixedClock);
    }

    @Test
    public void testFixedClock() {
        long epochSecond = Instant.now(fixedClock).getEpochSecond();

        LockSupport.parkNanos(1000 * 1000 * 1000 * 2); // 2 seconds

        long secondToCheck = Instant.now(fixedClock).getEpochSecond();
        assertEquals(epochSecond, secondToCheck);
    }

    @Test
    public void shouldThrottleUnAuthorizedUser() {
        ConcurrentMap<Boolean, Long> collect = IntStream.rangeClosed(1, REAL_RPS)
                .parallel()
                .mapToObj(userToken -> throttlingService.isRequestAllowed(null))
                .collect(Collectors.groupingByConcurrent(val -> val, Collectors.counting()));

        assertThat(collect).isNotEmpty();
        assertThat(collect).hasSize(2);
        assertThat(collect.get(true)).isEqualTo(GUEST_RPS);
        assertThat(collect.get(false)).isEqualTo(REAL_RPS - GUEST_RPS);

        assertThat(throttlingService.getRequestToSlaPerToken()).isEmpty();
        assertThat(throttlingService.getUserToUserDataSetMap()).isEmpty();

        //By default ThrottlingServiceImpl populate dummy values for authorized and unauthorized users.
        // That's why size == 2.
        assertThat(throttlingService.getTokenToUserDataMap()).hasSize(2);
    }

    @Test
    public void shouldThrottleAuthorizedUser() {
        //simulate different authorized users submit a token OR one user submit different tokens.
        //EmptySlaService to prevent updating Sla in ThrottlingService.
        throttlingService = new ThrottlingServiceImpl(GUEST_RPS, new EmptySlaService());
        throttlingService.setSystemClock(fixedClock);

        ConcurrentMap<Boolean, Long> collect = IntStream.rangeClosed(1, REAL_RPS)
                .parallel()
                .mapToObj(userToken -> throttlingService.isRequestAllowed(UUID.randomUUID().toString()))
                .collect(Collectors.groupingByConcurrent(val -> val, Collectors.counting()));

        assertThat(collect).isNotEmpty();
        assertThat(collect).hasSize(2);
        assertThat(collect.get(true)).isEqualTo(GUEST_RPS);
        assertThat(collect.get(false)).isEqualTo(REAL_RPS - GUEST_RPS);

        assertThat(throttlingService.getRequestToSlaPerToken()).isEmpty();
        assertThat(throttlingService.getUserToUserDataSetMap()).isEmpty();

        //By default ThrottlingServiceImpl populate dummy values for authorized and unauthorized users.
        // That's why size == 2.
        assertThat(throttlingService.getTokenToUserDataMap()).hasSize(2);
    }

    @Test
    public void shouldThrottleAuthorizedUserWithDelaySla() {
        final List<String> slaTokens = Lists.newArrayList(TOKEN_1_1, TOKEN_1_2, TOKEN_2_1, TOKEN_2_2);

        ConcurrentMap<Boolean, Long> collect = IntStream.rangeClosed(1, REAL_RPS)
                .parallel()
                .mapToObj(userToken -> throttlingService.isRequestAllowed(getToken(slaTokens, userToken)))
                .collect(Collectors.groupingByConcurrent(val -> val, Collectors.counting()));

        assertThat(collect).isNotEmpty();
        assertThat(collect).hasSize(2);
        assertThat(collect.get(true)).isEqualTo(GUEST_RPS);
        assertThat(collect.get(false)).isEqualTo(REAL_RPS - GUEST_RPS);

        //Is not empty, because SlaServiceStubWithDelay does work with 2 seconds delay
        // and then cleanup requestToSlaPerToken map
        assertThat(throttlingService.getRequestToSlaPerToken()).isNotEmpty();
        assertThat(throttlingService.getUserToUserDataSetMap()).isEmpty();

        //By default ThrottlingServiceImpl populate dummy values for authorized and unauthorized users.
        // That's why size == 2.
        assertThat(throttlingService.getTokenToUserDataMap()).hasSize(2);

        //SLA_DELAY + guarded time to ensure that CompletableFuture completes tasks.
        LockSupport.parkNanos(SLA_DELAY + GUARDED_PERIOD);
        assertThat(throttlingService.getRequestToSlaPerToken()).isEmpty();
        assertThat(throttlingService.getTokenToUserDataMap()).hasSize(2 + slaTokens.size());


        //increase second sequence in fixedClock
        fixedClock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        throttlingService.setSystemClock(fixedClock);

        //SlaService respond with Slas
        collect = IntStream.rangeClosed(1, REAL_RPS)
                .parallel()
                .mapToObj(userToken -> throttlingService.isRequestAllowed(getToken(slaTokens, userToken)))
                .collect(Collectors.groupingByConcurrent(val -> val, Collectors.counting()));

        assertThat(collect).isNotEmpty();
        assertThat(collect).hasSize(2);
        int totalsRps = USER_1_MAX_RPS + USER_2_MAX_RPS;
        assertThat(collect.get(true)).isEqualTo(totalsRps);
        assertThat(collect.get(false)).isEqualTo(REAL_RPS - totalsRps); // 25 - 24 == 1
    }

    @Test
    public void shouldThrottleAuthorizedUserWithDelaySlaWithUnknownTokens() {
        int guestRps = 5;
        int realRps = 10;
        long delay = 1000 * 1000 * 1000; // 1 second
        throttlingService = new ThrottlingServiceImpl(guestRps, new SlaServiceStubWithDelay(delay, false));
        throttlingService.setSystemClock(fixedClock);

        ConcurrentMap<Boolean, Long> collect = IntStream.rangeClosed(1, realRps)
                .parallel()
                .mapToObj(userToken -> throttlingService.isRequestAllowed(UUID.randomUUID().toString()))
                .collect(Collectors.groupingByConcurrent(val -> val, Collectors.counting()));

        assertThat(collect).isNotEmpty();
        assertThat(collect).hasSize(2);
        assertThat(collect.get(true)).isEqualTo(guestRps);
        assertThat(collect.get(false)).isEqualTo(realRps - guestRps);

        //Is not empty, because SlaServiceStubWithDelay does work with 2 seconds delay
        // and then cleanup requestToSlaPerToken map
        assertThat(throttlingService.getRequestToSlaPerToken()).isNotEmpty();
        assertThat(throttlingService.getUserToUserDataSetMap()).isEmpty();

        //By default ThrottlingServiceImpl populate dummy values for authorized and unauthorized users.
        // That's why size == 2.
        assertThat(throttlingService.getTokenToUserDataMap()).hasSize(2);

        //SLA_DELAY + guarded time to ensure that CompletableFuture complete tasks.
        long slaDelayTotal = realRps * delay;
        LockSupport.parkNanos(slaDelayTotal + GUARDED_PERIOD);
        assertThat(throttlingService.getRequestToSlaPerToken()).isEmpty();
    }

    @Test
    public void shouldThrottleAuthorizedMizedUsersWithoutAndWithSla() {
        //simulate different authorized users submit a token OR one user submit different tokens.
        //EmptySlaService to prevent updating Sla in ThrottlingService.
        final List<String> slaTokens = Lists.newArrayList(TOKEN_1_1, TOKEN_1_2, TOKEN_2_1, TOKEN_2_2);

        Map<String, ThrottlingServiceImpl.UserData> map = new ConcurrentHashMap<>();
        map.put(TOKEN_1_1, new ThrottlingServiceImpl.UserData(Instant.now(fixedClock).getEpochSecond(), USER_1_SLA, USER_1_MAX_RPS - 2, TOKEN_1_1));
        map.put(TOKEN_1_2, new ThrottlingServiceImpl.UserData(Instant.now(fixedClock).getEpochSecond(), USER_1_SLA, USER_1_MAX_RPS - 2, TOKEN_1_2));
        map.put(TOKEN_2_1, new ThrottlingServiceImpl.UserData(Instant.now(fixedClock).getEpochSecond(), USER_2_SLA, USER_2_MAX_RPS - 2, TOKEN_2_1));
        map.put(TOKEN_2_2, new ThrottlingServiceImpl.UserData(Instant.now(fixedClock).getEpochSecond(), USER_2_SLA, USER_2_MAX_RPS - 2, TOKEN_2_2));

        throttlingService = new ThrottlingServiceImpl(GUEST_RPS, new EmptySlaService());
        throttlingService.getTokenToUserDataMap().forEach(map::put);
        throttlingService.setSystemClock(fixedClock);
        Field field = ReflectionUtils.findField(ThrottlingServiceImpl.class, "tokenToUserDataMap");
        ReflectionUtils.makeAccessible(field);
        ReflectionUtils.setField(field, throttlingService, map);

        ConcurrentMap<Boolean, Long> withoutSla = IntStream.rangeClosed(1, REAL_RPS)
                .parallel()
                .mapToObj(userToken -> throttlingService.isRequestAllowed(UUID.randomUUID().toString()))
                .collect(Collectors.groupingByConcurrent(val -> val, Collectors.counting()));

        ConcurrentMap<Boolean, Long> withSla = IntStream.rangeClosed(1, REAL_RPS)
                .parallel()
                .mapToObj(userToken -> throttlingService.isRequestAllowed(getToken(slaTokens, userToken)))
                .collect(Collectors.groupingByConcurrent(val -> val, Collectors.counting()));

        assertThat(withoutSla).isNotEmpty();
        assertThat(withoutSla).hasSize(2);
        assertThat(withoutSla.get(true)).isEqualTo(GUEST_RPS);
        assertThat(withoutSla.get(false)).isEqualTo(REAL_RPS - GUEST_RPS);

        assertThat(withSla).isNotEmpty();
        assertThat(withSla).hasSize(2);
        // -4, because for each token -2 when we populate tokens. each user has 2 tokens.
        int totalsRps = USER_1_MAX_RPS - 4 + USER_2_MAX_RPS - 4;
        assertThat(withSla.get(true)).isEqualTo(totalsRps);
        assertThat(withSla.get(false)).isEqualTo(REAL_RPS - totalsRps);

    }

    private String getToken(List<String> slaTokens, int index) {
        return slaTokens.get((slaTokens.size() - 1) & index);
    }
}
