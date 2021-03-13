package com.secretcompany.service;

import com.secretcompany.dto.Sla;

import java.util.concurrent.CompletableFuture;

/**
 * Returns Sla per user's token
 */
@FunctionalInterface
public interface SlaService {
    CompletableFuture<Sla> getSlaByToken(String token);
}
