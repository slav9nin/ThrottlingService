package com.secretcompany.service.impl;

import com.secretcompany.dto.Sla;
import com.secretcompany.mock.EmptySlaService;
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

        throttlingService = new WindowThrottlingServiceImpl(GUEST_RPS, new EmptySlaService());
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

        throttlingService.setSystemClock(plus1000Clock);

        collect = IntStream.rangeClosed(1, 4 * REAL_RPS)
                .parallel()
                .mapToObj(userToken -> throttlingService.isRequestAllowed(null))
                .collect(Collectors.groupingByConcurrent(val -> val, Collectors.counting()));

        assertThat(collect).isNotEmpty();
        assertThat(collect).hasSize(1);
        assertThat(collect.get(true)).isEqualTo(4 * REAL_RPS);

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

        //simulate that out 1s window.
        throttlingService.setSystemClock(plus1000Clock);

        collect = IntStream.rangeClosed(1, GUEST_RPS + 1)
                .parallel()
                .mapToObj(userToken -> throttlingService.isRequestAllowed(null))
                .collect(Collectors.groupingByConcurrent(val -> val, Collectors.counting()));

        assertThat(collect).isNotEmpty();
        assertThat(collect).hasSize(2);
        assertThat(collect.get(true)).isEqualTo(GUEST_RPS);
        assertThat(collect.get(false)).isEqualTo(1);

        assertThat(throttlingService.getRequestToSlaPerToken()).isEmpty();
    }

    @Test
    public void shouldThrottleAuthorizedUserWithoutSla() {
        //path fixed clock
        throttlingService.setSystemClock(fixedClock);

        ConcurrentMap<Boolean, Long> collect = IntStream.rangeClosed(1, REAL_RPS)
                .parallel()
                .mapToObj(userToken -> throttlingService.isRequestAllowed(UUID.randomUUID().toString()))
                .collect(Collectors.groupingByConcurrent(val -> val, Collectors.counting()));

        assertThat(collect).isNotEmpty();
        assertThat(collect).hasSize(1);
        assertThat(collect.get(true)).isEqualTo(REAL_RPS);

        //simulate that time went through 500 ml.
        throttlingService.setSystemClock(plus500Clock);

        collect = IntStream.rangeClosed(1, REAL_RPS)
                .parallel()
                .mapToObj(userToken -> throttlingService.isRequestAllowed(UUID.randomUUID().toString()))
                .collect(Collectors.groupingByConcurrent(val -> val, Collectors.counting()));

        assertThat(collect).isNotEmpty();
        assertThat(collect).hasSize(1);
        assertThat(collect.get(true)).isEqualTo(REAL_RPS);

        //simulate that out 1s window.
        throttlingService.setSystemClock(plus1000Clock);

        collect = IntStream.rangeClosed(1, GUEST_RPS + 1)
                .parallel()
                .mapToObj(userToken -> throttlingService.isRequestAllowed(UUID.randomUUID().toString()))
                .collect(Collectors.groupingByConcurrent(val -> val, Collectors.counting()));

        assertThat(collect).isNotEmpty();
        assertThat(collect).hasSize(2);
        assertThat(collect.get(true)).isEqualTo(GUEST_RPS);
        assertThat(collect.get(false)).isEqualTo(1);

        assertThat(throttlingService.getRequestToSlaPerToken()).isEmpty();
    }

    @Test
    public void shouldThrottleSlaUsers() {
        final List<String> slaTokens1 = Lists.newArrayList(TOKEN_1_1, TOKEN_1_2);
        final List<String> slaTokens2 = Lists.newArrayList(TOKEN_2_1, TOKEN_2_2);
        final Map<String, Sla> map = new ConcurrentHashMap<>();
        map.put(TOKEN_1_1, USER_1_SLA);
        map.put(TOKEN_1_2, USER_1_SLA);
        map.put(TOKEN_2_1, USER_2_SLA);
        map.put(TOKEN_2_2, USER_2_SLA);

        Field field = ReflectionUtils.findField(WindowThrottlingServiceImpl.class, "tokenSlaMap");
        ReflectionUtils.makeAccessible(field);
        ReflectionUtils.setField(field, throttlingService, map);

        throttlingService.setSystemClock(fixedClock);

        ConcurrentMap<Boolean, Long> collect = IntStream.rangeClosed(1, USER_1_MAX_RPS)
                .parallel()
                .mapToObj(userToken -> throttlingService.isRequestAllowed(getToken(slaTokens1, userToken)))
                .collect(Collectors.groupingByConcurrent(val -> val, Collectors.counting()));

        assertThat(collect).isNotEmpty();
        assertThat(collect).hasSize(1);
        assertThat(collect.get(true)).isEqualTo(USER_1_MAX_RPS);

        collect = IntStream.rangeClosed(1, USER_2_MAX_RPS)
                .parallel()
                .mapToObj(userToken -> throttlingService.isRequestAllowed(getToken(slaTokens2, userToken)))
                .collect(Collectors.groupingByConcurrent(val -> val, Collectors.counting()));

        assertThat(collect).isNotEmpty();
        assertThat(collect).hasSize(1);
        assertThat(collect.get(true)).isEqualTo(USER_2_MAX_RPS);

        //simulate that time went through 500 ml.
        throttlingService.setSystemClock(plus500Clock);

        collect = IntStream.rangeClosed(1, USER_1_MAX_RPS)
                .parallel()
                .mapToObj(userToken -> throttlingService.isRequestAllowed(getToken(slaTokens1, userToken)))
                .collect(Collectors.groupingByConcurrent(val -> val, Collectors.counting()));

        assertThat(collect).isNotEmpty();
        assertThat(collect).hasSize(1);
        assertThat(collect.get(false)).isEqualTo(USER_1_MAX_RPS);

        collect = IntStream.rangeClosed(1, USER_2_MAX_RPS)
                .parallel()
                .mapToObj(userToken -> throttlingService.isRequestAllowed(getToken(slaTokens2, userToken)))
                .collect(Collectors.groupingByConcurrent(val -> val, Collectors.counting()));

        assertThat(collect).isNotEmpty();
        assertThat(collect).hasSize(1);
        assertThat(collect.get(false)).isEqualTo(USER_2_MAX_RPS);

        //simulate that out 1s. window
        throttlingService.setSystemClock(plus1000Clock);

        collect = IntStream.rangeClosed(1, USER_1_MAX_RPS + 1)
                .parallel()
                .mapToObj(userToken -> throttlingService.isRequestAllowed(getToken(slaTokens1, userToken)))
                .collect(Collectors.groupingByConcurrent(val -> val, Collectors.counting()));

        assertThat(collect).isNotEmpty();
        assertThat(collect).hasSize(2);
        assertThat(collect.get(true)).isEqualTo(USER_1_MAX_RPS);
        assertThat(collect.get(false)).isEqualTo(1);

        collect = IntStream.rangeClosed(1, USER_2_MAX_RPS + 1)
                .parallel()
                .mapToObj(userToken -> throttlingService.isRequestAllowed(getToken(slaTokens2, userToken)))
                .collect(Collectors.groupingByConcurrent(val -> val, Collectors.counting()));

        assertThat(collect).isNotEmpty();
        assertThat(collect).hasSize(2);
        assertThat(collect.get(true)).isEqualTo(USER_2_MAX_RPS);
        assertThat(collect.get(false)).isEqualTo(1);

    }

    private String getToken(List<String> slaTokens, int index) {
        return slaTokens.get((slaTokens.size() - 1) & index);
    }
}
