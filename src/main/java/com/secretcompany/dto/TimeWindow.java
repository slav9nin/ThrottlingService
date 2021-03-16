package com.secretcompany.dto;


import java.util.Objects;

public class TimeWindow {
    private final long startMillis;
    private final long endMillis;
    private final long rps;

    public TimeWindow(long startMillis, long endMillis, long rps) {
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.rps = rps;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public long getEndMillis() {
        return endMillis;
    }

    public long getRps() {
        return rps;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TimeWindow that = (TimeWindow) o;
        return startMillis == that.startMillis && endMillis == that.endMillis && rps == that.rps;
    }

    @Override
    public int hashCode() {
        return Objects.hash(startMillis, endMillis, rps);
    }
}
