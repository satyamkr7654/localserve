package com.localserve.location.domain;

import com.localserve.shared.identity.PublicId;

import java.time.Instant;
import java.util.Objects;

public record LocationSample(PublicId providerId, GeoPoint point, double accuracyMeters,
                             long deviceSequence, Instant recordedAt) {
    public LocationSample {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(recordedAt, "recordedAt");
        if (!Double.isFinite(accuracyMeters) || accuracyMeters < 0 || deviceSequence < 0) {
            throw new IllegalArgumentException("invalid location sample metadata");
        }
    }
}
