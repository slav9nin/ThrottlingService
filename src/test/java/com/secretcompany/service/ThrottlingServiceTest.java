package com.secretcompany.service;

import com.google.common.collect.Lists;
import com.secretcompany.service.impl.ThrottlingServiceImpl;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ThrottlingServiceTest {

    private static final int GUEST_RPS = 15;
    private static final int REAL_RPS = 25;

    private ThrottlingService throttlingService= new ThrottlingServiceImpl(GUEST_RPS);

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
