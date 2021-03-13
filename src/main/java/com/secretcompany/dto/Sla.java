package com.secretcompany.dto;

public class Sla {
    private final String user;
    private final int rps;

    public Sla(final String user, final int rps) {
        this.user = user;
        this.rps = rps;
    }

    public String getUser() {
        return this.user;
    }

    public int getRps() {
        return this.rps;
    }
}
