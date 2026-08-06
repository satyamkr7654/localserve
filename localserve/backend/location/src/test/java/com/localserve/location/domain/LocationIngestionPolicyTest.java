package com.localserve.location.domain;

import com.localserve.shared.error.DomainException;
import com.localserve.shared.identity.PublicId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class LocationIngestionPolicyTest {
    @Test void rejectsReplayAndPoorAccuracy() {
        Instant now = Instant.parse("2026-08-06T10:00:00Z");
        var policy = new LocationIngestionPolicy(Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(2), 100);
        PublicId provider = PublicId.generate();
        var current = new LocationSample(provider, new GeoPoint(77.1, 28.6), 10, 5, now.minusSeconds(5));
        assertThrows(DomainException.class, () -> policy.requireAccepted(
                new LocationSample(provider, new GeoPoint(77.2, 28.7), 10, 5, now), current));
        assertThrows(DomainException.class, () -> policy.requireAccepted(
                new LocationSample(provider, new GeoPoint(77.2, 28.7), 150, 6, now), current));
    }
}
