package com.localserve.people.provider;

import com.localserve.shared.identity.PublicId;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record ProviderEligibilitySnapshot(
        PublicId providerId,
        ProviderVerificationStatus verificationStatus,
        boolean accountActive,
        boolean online,
        boolean acceptingWork,
        Instant lastLocationAt,
        Set<PublicId> approvedServiceIds,
        Set<PublicId> activeBookingIds) {
    public ProviderEligibilitySnapshot {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(verificationStatus, "verificationStatus");
        Objects.requireNonNull(lastLocationAt, "lastLocationAt");
        approvedServiceIds = Set.copyOf(Objects.requireNonNull(approvedServiceIds, "approvedServiceIds"));
        activeBookingIds = Set.copyOf(Objects.requireNonNull(activeBookingIds, "activeBookingIds"));
    }
}
