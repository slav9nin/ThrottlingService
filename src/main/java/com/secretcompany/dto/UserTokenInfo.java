package com.secretcompany.dto;

import java.util.Objects;
import java.util.Set;

public class UserTokenInfo {
    private final String userName;
    private final Set<String> tokens;

    public UserTokenInfo(String userName, Set<String> tokens) {
        Objects.requireNonNull(userName, "UserName is required");
        Objects.requireNonNull(tokens, "tokenSet is required");
        this.userName = userName;
        this.tokens = tokens;
    }

    public String getUserName() {
        return userName;
    }

    public Set<String> getTokens() {
        return tokens;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserTokenInfo that = (UserTokenInfo) o;
        return Objects.equals(userName, that.userName) && Objects.equals(tokens, that.tokens);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userName, tokens);
    }
}
