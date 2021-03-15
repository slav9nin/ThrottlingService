package com.secretcompany.dto;

import com.google.common.base.MoreObjects;

import java.util.Objects;

/**
 * Immutable
 */
public class Sla {
    private final String user;
    private final long rps;

    public Sla(final String user, final long rps) {
        Objects.requireNonNull(user, "USer should not be null");
        this.user = user;
        this.rps = rps;
    }

    public String getUser() {
        return this.user;
    }

    public long getRps() {
        return this.rps;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Sla sla = (Sla) o;
        return rps == sla.rps && Objects.equals(user, sla.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, rps);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("user", user)
                .add("rps", rps)
                .toString();
    }
}
