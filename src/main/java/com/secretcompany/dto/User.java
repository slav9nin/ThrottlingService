package com.secretcompany.dto;

import com.google.common.annotations.VisibleForTesting;
import com.secretcompany.annotation.NotThreadSafe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implement and support only valuable(required) fields to fit original task.
 */
@NotThreadSafe
public class User {

    private final UUID userId;

    // Omit user specific info for this task. It's unnecessary...
    // private final String userName;
    // other user specific fields...

    private final List<String> userTokens;

    public User() {
        this.userId = UUID.randomUUID();
        this.userTokens = new ArrayList<>();
    }

    public User(List<String> userTokens) {
        this.userTokens = userTokens;
        this.userId = UUID.randomUUID();
    }

    @VisibleForTesting
    User(UUID userId, List<String> userTokens) {
        this.userId = userId;
        this.userTokens = userTokens;
    }

    public List<String> getUserTokens() {
        return new ArrayList<>(userTokens);
    }
}
