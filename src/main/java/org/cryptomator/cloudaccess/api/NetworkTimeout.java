package org.cryptomator.cloudaccess.api;

import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;

public enum NetworkTimeout {

    CONNECTION(30L, SECONDS), //
    READ(30L, SECONDS), //
    WRITE(30L, SECONDS);

    private final long timeout;
    private final TimeUnit unit;

    NetworkTimeout(final long timeout, final TimeUnit unit) {
        this.timeout = timeout;
        this.unit = unit;
    }

    public long getTimeout() {
        return timeout;
    }

    public TimeUnit getUnit() {
        return unit;
    }

    public long asMilliseconds() {
        return unit.toMillis(timeout);
    }
}
