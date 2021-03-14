package com.secretcompany.dto;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implement and support only valuable(required) fields to fit original task.
 */
public class User {

    private final String userId;
    private final List<String> userTokens;

    public User() {
        this.userId = UUID.randomUUID().toString();
        this.userTokens = new ArrayList<>();
    }

    public User(List<String> userTokens) {
        this.userId = UUID.randomUUID().toString();
        this.userTokens = userTokens;
    }

    @VisibleForTesting
    User(UUID userId, List<String> userTokens) {
        this.userId = userId.toString();
        this.userTokens = userTokens;
    }

    @VisibleForTesting
    User(String userId, List<String> userTokens) {
        this.userId = userId;
        this.userTokens = userTokens;
    }

    public List<String> getUserTokens() {
        return new ArrayList<>(userTokens);
    }
}
