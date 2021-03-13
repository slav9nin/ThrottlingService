package com.secretcompany.service;

/**
 * Concerns regarding ThrottlingService. How we determine User if token is null? We cannot do it.
 *   If different users submit nullable tokens we cannot distinguish them. Based on it, we can handle it only if:
 *   1. Token is NotNull and contains userId. String token = "<userUUID>:<token>". isRequestAllowed(@NotNull String token)
 *   2. Change method signature and add final UserId. isRequestAllowed(String token, @NotNull String userId)
 *   I prefer the last one! Any objections?
 */
@FunctionalInterface
public interface AdvancedThrottlingService {
    /**
     * @return true if request is within allowed request per second (RPS) or false otherwise
     */
    boolean isRequestAllowed(final String token, final String userId);
}
