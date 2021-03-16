package com.secretcompany.dto;

import java.util.Objects;

public class UserData {
    private final long secondId;
    private final Sla sla;
    private final long rps;
    private final String token;

    public UserData(long secondId, Sla sla, long rps, String token) {
        Objects.requireNonNull(sla, "Sla should not be null");
        Objects.requireNonNull(token, "token Set should not be null");
        this.secondId = secondId;
        this.sla = sla;
        this.rps = rps;
        this.token = token;
    }

    public long getSecondId() {
        return secondId;
    }

    public Sla getSla() {
        return sla;
    }

    public long getRps() {
        return rps;
    }

    public String getToken() {
        return token;
    }

    //except Sla and UserId
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserData userData = (UserData) o;
        return secondId == userData.secondId && rps == userData.rps && Objects.equals(token, userData.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(secondId, rps, token);
    }
}