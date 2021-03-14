package com.secretcompany.mock;

import com.secretcompany.dto.Sla;
import com.secretcompany.service.SlaService;

import java.util.concurrent.CompletableFuture;

/**
 * For testing purpose. Always return null on each token.
 * To prevent updating Sla in {@link com.secretcompany.service.impl.ThrottlingServiceImpl}
 * Complete CompletableFuture in the same Thread immediately.
 */
public class EmptySlaService implements SlaService {
    @Override
    public CompletableFuture<Sla> getSlaByToken(String token) {
        return CompletableFuture.completedFuture(null);
    }
}
