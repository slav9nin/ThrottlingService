package com.secretcompany.service.impl;

import com.secretcompany.mock.SlaServiceStubWithDelay;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class WindowThrottlingServiceImplTest {

    private static final int GUEST_RPS = 20;
    private static final int REAL_RPS = 5;
    private static final long SLA_DELAY = 1000L * 1000 * 1000 * 3; //3 seconds
    private static final long GUARDED_PERIOD = 1000L * 1000 * 1000; //1 second

    //Using Implementation instead of Interface to get access to package-private(VisibleForTesting) methods!
    private WindowThrottlingServiceImpl throttlingService ;

    private Instant now;
    private Clock fixedClock ;
    private Instant plus500Instant;
    private Instant plus999Instant;
    private Instant plus1000Instant;
    private Clock plus500Clock;
    private Clock plus999Clock;
    private Clock plus1000Clock;

    @Before
    public void init() {
        now = Instant.now();
        fixedClock = Clock.fixed(now, ZoneId.systemDefault());
        plus500Instant = now.plusMillis(500L);
        plus999Instant = now.plusMillis(999L);
        plus1000Instant = now.plusMillis(1000);
        plus500Clock = Clock.fixed(plus500Instant, ZoneId.systemDefault());
        plus999Clock = Clock.fixed(plus999Instant, ZoneId.systemDefault());
        plus1000Clock = Clock.fixed(plus1000Instant, ZoneId.systemDefault());

        throttlingService = new WindowThrottlingServiceImpl(GUEST_RPS, new SlaServiceStubWithDelay(SLA_DELAY, false));
    }

    @Test
    public void shouldThrottleUnAuthorizedUserWithinOneSecond() {
        //path fixed clock
        throttlingService.setSystemClock(fixedClock);

        ConcurrentMap<Boolean, Long> collect = IntStream.rangeClosed(1, REAL_RPS)
                .parallel()
                .mapToObj(userToken -> throttlingService.isRequestAllowed(null))
                .collect(Collectors.groupingByConcurrent(val -> val, Collectors.counting()));

        assertThat(collect).isNotEmpty();
        assertThat(collect).hasSize(1);
        assertThat(collect.get(true)).isEqualTo(REAL_RPS);

        assertThat(throttlingService.getRequestToSlaPerToken()).isEmpty();

        //simulate that time went through 500 ml.
        throttlingService.setSystemClock(plus500Clock);

        collect = IntStream.rangeClosed(1, REAL_RPS)
                .parallel()
                .mapToObj(userToken -> throttlingService.isRequestAllowed(null))
                .collect(Collectors.groupingByConcurrent(val -> val, Collectors.counting()));

        assertThat(collect).isNotEmpty();
        assertThat(collect).hasSize(1);
        assertThat(collect.get(true)).isEqualTo(REAL_RPS);

        assertThat(throttlingService.getRequestToSlaPerToken()).isEmpty();

        //simulate that time went through 999 ml. < 1 s.
        throttlingService.setSystemClock(plus999Clock);

        collect = IntStream.rangeClosed(1, 3 * REAL_RPS)
                .parallel()
                .mapToObj(userToken -> throttlingService.isRequestAllowed(null))
                .collect(Collectors.groupingByConcurrent(val -> val, Collectors.counting()));

        assertThat(collect).isNotEmpty();
        assertThat(collect).hasSize(2);
        assertThat(collect.get(true)).isEqualTo(2 * REAL_RPS);
        assertThat(collect.get(false)).isEqualTo(REAL_RPS);

        assertThat(throttlingService.getRequestToSlaPerToken()).isEmpty();
    }

    @Test
    public void shouldThrottleUnAuthorizedUser() {
        //path fixed clock
        throttlingService.setSystemClock(fixedClock);

        ConcurrentMap<Boolean, Long> collect = IntStream.rangeClosed(1, REAL_RPS)
                .parallel()
                .mapToObj(userToken -> throttlingService.isRequestAllowed(null))
                .collect(Collectors.groupingByConcurrent(val -> val, Collectors.counting()));

        assertThat(collect).isNotEmpty();
        assertThat(collect).hasSize(1);
        assertThat(collect.get(true)).isEqualTo(REAL_RPS);

        assertThat(throttlingService.getRequestToSlaPerToken()).isEmpty();

        //simulate that time went through 500 ml.
        throttlingService.setSystemClock(plus500Clock);

        collect = IntStream.rangeClosed(1, REAL_RPS)
                .parallel()
                .mapToObj(userToken -> throttlingService.isRequestAllowed(null))
                .collect(Collectors.groupingByConcurrent(val -> val, Collectors.counting()));

        assertThat(collect).isNotEmpty();
        assertThat(collect).hasSize(1);
        assertThat(collect.get(true)).isEqualTo(REAL_RPS);

        assertThat(throttlingService.getRequestToSlaPerToken()).isEmpty();

        //simulate that time went through 999 ml. < 1 s.
        throttlingService.setSystemClock(plus999Clock);

        collect = IntStream.rangeClosed(1, REAL_RPS)
                .parallel()
                .mapToObj(userToken -> throttlingService.isRequestAllowed(null))
                .collect(Collectors.groupingByConcurrent(val -> val, Collectors.counting()));

        assertThat(collect).isNotEmpty();
        assertThat(collect).hasSize(1);
        assertThat(collect.get(true)).isEqualTo(REAL_RPS);

        assertThat(throttlingService.getRequestToSlaPerToken()).isEmpty();
    }

}
