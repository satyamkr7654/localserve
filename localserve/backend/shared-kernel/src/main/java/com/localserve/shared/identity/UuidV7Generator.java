package com.localserve.shared.identity;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** Generates RFC 9562 UUIDv7 identifiers without leaking host or process data. */
public final class UuidV7Generator {
    private static final long TIMESTAMP_MASK = 0x0000_FFFF_FFFF_FFFFL;
    private static final long RAND_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL;

    private final Clock clock;
    private final SecureRandom random;

    public UuidV7Generator() {
        this(Clock.systemUTC(), new SecureRandom());
    }

    public UuidV7Generator(Clock clock, SecureRandom random) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    public UUID generate() {
        long unixMillis = clock.millis();
        if (unixMillis < 0 || unixMillis > TIMESTAMP_MASK) {
            throw new IllegalStateException("clock is outside the UUIDv7 timestamp range");
        }
        long randomA = random.nextInt(1 << 12);
        long mostSignificantBits = (unixMillis << 16) | 0x7000L | randomA;
        long leastSignificantBits = 0x8000_0000_0000_0000L | (random.nextLong() & RAND_B_MASK);
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
