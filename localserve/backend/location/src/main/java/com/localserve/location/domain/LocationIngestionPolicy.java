package com.localserve.location.domain;

import com.localserve.shared.error.DomainException;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

public final class LocationIngestionPolicy {
    private final Clock clock;
    private final Duration maximumAge;
    private final double maximumAccuracyMeters;

    public LocationIngestionPolicy(Clock clock, Duration maximumAge, double maximumAccuracyMeters) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maximumAge = Objects.requireNonNull(maximumAge, "maximumAge");
        if (maximumAge.isNegative() || maximumAge.isZero() || maximumAccuracyMeters <= 0) {
            throw new IllegalArgumentException("invalid location ingestion policy");
        }
        this.maximumAccuracyMeters = maximumAccuracyMeters;
    }

    public void requireAccepted(LocationSample incoming, LocationSample current) {
        Objects.requireNonNull(incoming, "incoming");
        if (incoming.recordedAt().isBefore(clock.instant().minus(maximumAge))) {
            throw new DomainException("LOCATION.SAMPLE_STALE", "Location sample is too old");
        }
        if (incoming.recordedAt().isAfter(clock.instant().plusSeconds(30))) {
            throw new DomainException("LOCATION.CLOCK_SKEW", "Location sample is in the future");
        }
        if (incoming.accuracyMeters() > maximumAccuracyMeters) {
            throw new DomainException("LOCATION.ACCURACY_LOW", "Location accuracy is insufficient");
        }
        if (current != null && (!incoming.providerId().equals(current.providerId())
                || incoming.deviceSequence() <= current.deviceSequence()
                || !incoming.recordedAt().isAfter(current.recordedAt()))) {
            throw new DomainException("LOCATION.OUT_OF_ORDER", "Location sample is out of order");
        }
    }
}
