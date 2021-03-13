package com.secretcompany.service;

@FunctionalInterface
public interface ThrottlingService {

    /**
     * @return true if request is within allowed request per second (RPS) or false otherwise
     */
    boolean isRequestAllowed(String token);
}
