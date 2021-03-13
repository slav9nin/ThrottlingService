package com.secretcompany.config;

import java.util.concurrent.ForkJoinPool;

public class ThrottlingConfiguration {
    public static final ForkJoinPool FORK_JOIN_POOL = new ForkJoinPool();
}
