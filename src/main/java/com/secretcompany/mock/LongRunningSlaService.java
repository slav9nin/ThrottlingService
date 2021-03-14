package com.secretcompany.mock;

import com.secretcompany.dto.Sla;
import com.secretcompany.service.SlaService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.LockSupport;

/**
 * For testing purpose.
 * Delay in request thread.
 */
public class LongRunningSlaService implements SlaService {
    @Override
    public CompletableFuture<Sla> getSlaByToken(String token) {
        LockSupport.parkNanos(1000 * 1000 * 1000  * 2); //2 seconds
        return CompletableFuture.completedFuture(null);
    }
}
