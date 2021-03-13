package com.secretcompany.mock;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.secretcompany.dto.Sla;
import com.secretcompany.service.SlaService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;

public class SlaServiceStub implements SlaService {

    private static final String TOKEN_1_1 = UUID.randomUUID().toString();
    private static final String TOKEN_1_2 = UUID.randomUUID().toString();
    private static final String TOKEN_2_1 = UUID.randomUUID().toString();
    private static final String TOKEN_2_2 = UUID.randomUUID().toString();

    private static final String USER_1 = "User1";
    private static final String USER_2 = "User2";

    private static final int USER_1_MAX_RPS = 8;
    private static final int USER_2_MAX_RPS = 16;

    private final Map<String, Sla> predefinedMapping;
    private final ExecutorService threadPool;

    public SlaServiceStub() {
        ThreadFactoryBuilder threadFactoryBuilder = new ThreadFactoryBuilder().setNameFormat("SlaWorkerThread-%s");
        threadPool = Executors
                .newFixedThreadPool(Runtime.getRuntime().availableProcessors(), threadFactoryBuilder.build());
        predefinedMapping = initializeStub();
    }

    @Override
    public CompletableFuture<Sla> getSlaByToken(final String token) {
        return CompletableFuture.supplyAsync(() -> retrieveSla(token), threadPool);
    }

    /**
     * Simulate long running operation
     * @param token - User token
     * @return Sla for particular token
     */
    private Sla retrieveSla(final String token) {
        ThreadLocalRandom threadLocalRandom = ThreadLocalRandom.current();
        int delay = threadLocalRandom.nextInt(300);

        LockSupport.parkNanos(delay * 1000 * 1000);

        return predefinedMapping.get(token);
    }

    private Map<String, Sla> initializeStub() {
        final Map<String, Sla> predefinedMapping = new HashMap<>();
        final Sla sla1 = new Sla(USER_1, USER_1_MAX_RPS);
        final Sla sla2 = new Sla(USER_2, USER_2_MAX_RPS);

        predefinedMapping.put(TOKEN_1_1, sla1);
        predefinedMapping.put(TOKEN_1_2, sla1);
        predefinedMapping.put(TOKEN_2_1, sla2);
        predefinedMapping.put(TOKEN_2_2, sla2);

        return predefinedMapping;
    }
}
