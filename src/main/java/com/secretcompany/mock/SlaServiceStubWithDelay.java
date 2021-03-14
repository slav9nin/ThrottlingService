package com.secretcompany.mock;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.secretcompany.dto.Sla;
import com.secretcompany.service.SlaService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;

import static com.secretcompany.mock.SlaServiceStubConstants.TOKEN_1_1;
import static com.secretcompany.mock.SlaServiceStubConstants.TOKEN_1_2;
import static com.secretcompany.mock.SlaServiceStubConstants.TOKEN_2_1;
import static com.secretcompany.mock.SlaServiceStubConstants.TOKEN_2_2;
import static com.secretcompany.mock.SlaServiceStubConstants.USER_1;
import static com.secretcompany.mock.SlaServiceStubConstants.USER_1_MAX_RPS;
import static com.secretcompany.mock.SlaServiceStubConstants.USER_2;
import static com.secretcompany.mock.SlaServiceStubConstants.USER_2_MAX_RPS;

/**
 * Stub. For testing purpose.
 */
public class SlaServiceStubWithDelay implements SlaService {

    private static final long DEFAULT_DELAY = 1000 * 1000 * 300;//within 300 ms
    private final Map<String, Sla> predefinedMapping;
    private final ExecutorService threadPool;
    private final long slaDelay;
    private final boolean randomizeDelay;

    public SlaServiceStubWithDelay(long delay, boolean randomizeDelay) {
        if (delay < 0) {
            throw new RuntimeException("Delay should be >= 0. Zero delay means default one == 300 ms");
        } else if (delay == 0) {
            this.slaDelay = DEFAULT_DELAY;
        } else {
            this.slaDelay = delay;
        }
        this.randomizeDelay = randomizeDelay;
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
        long delay;
        if (randomizeDelay) {
            ThreadLocalRandom threadLocalRandom = ThreadLocalRandom.current();
            delay = threadLocalRandom.nextLong(300) * 1000 * 1000; // within 300 ms
        } else {
            delay = slaDelay;
        }

        LockSupport.parkNanos(delay);

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
