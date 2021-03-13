package com.secretcompany.service;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;

public class ThrottlingServiceTest {

    private static final int GUEST_RPS = 15;
    private static final int REAL_RPS = 25;

//    private ThrottlingService throttlingService= new ThrottlingServiceImpl(GUEST_RPS);

    @Test
    public void name() throws InterruptedException {
        Clock clock = Clock.systemUTC();
        Instant instant = Instant.now(clock);
        long timeStampSeconds = instant.getEpochSecond();
        System.out.println(timeStampSeconds);

        Thread.sleep(1000);

        Instant instant2 = Instant.now(clock);
        long timeStampSeconds2 = instant2.getEpochSecond();
        System.out.println(timeStampSeconds2);
    }

    @Test
    public void shouldThrottleUnAuthorizedUser() {
//        List<String> userFirstTokens = Lists.newArrayList("userFirst.1", "userFirst.2");
//        List<String> userFSecondTokens = Lists.newArrayList("userSecond.1", "userSecond.2", "userSecond.3");

//        List<Boolean> userFirstResult = IntStream.rangeClosed(1, 25)
//                .mapToObj(userToken -> throttlingService.isRequestAllowed(null))
//                .collect(Collectors.toList());
//
//        List<Boolean> userSecondResult = IntStream.rangeClosed(1, 25)
//                .mapToObj(userToken -> throttlingService.isRequestAllowed(null))
//                .collect(Collectors.toList());
//
//        System.out.println(userFirstResult);
//        System.out.println(userSecondResult);
    }

    @Test
    public void shouldThrottleAuthorizedUser() {
//        List<String> userFirstTokens = Lists.newArrayList("userFirst.1", "userFirst.2");
//        List<String> userFSecondTokens = Lists.newArrayList("userSecond.1", "userSecond.2", "userSecond.3");
//
//        List<Boolean> userFirstResult = IntStream.rangeClosed(1, 25)
//                .mapToObj(i -> userFirstTokens.get(userFirstTokens.size() % i))
//                .map(userToken -> throttlingService.isRequestAllowed(userToken))
//                .collect(Collectors.toList());
//
//        List<Boolean> userSecondResult = IntStream.rangeClosed(1, 25)
//                .mapToObj(i -> userFSecondTokens.get(userFSecondTokens.size() % i))
//                .map(userToken -> throttlingService.isRequestAllowed(userToken))
//                .collect(Collectors.toList());
//
//        System.out.println(userFirstResult);
//        System.out.println(userSecondResult);
    }
}
