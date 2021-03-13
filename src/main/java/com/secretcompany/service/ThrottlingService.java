package com.secretcompany.service;

/**
 * Concerns regarding ThrottlingService. How we determine User if token is null? We cannot do it.
 *   If method signature for (isRequestAllowed) cannot be changed -> all we can it's:
 *   restrict RPS for all unauthorized users(w/o tokens).
 *   For ex.: If GuestRPS == 20 and 21 users tries to access to ThrottlingService within particular second,
 *   only first 20 can do it and the last one will be looser.
 */
@FunctionalInterface
public interface ThrottlingService {

    /**
     * @return true if request is within allowed request per second (RPS) or false otherwise
     */
    boolean isRequestAllowed(final String token);
}
