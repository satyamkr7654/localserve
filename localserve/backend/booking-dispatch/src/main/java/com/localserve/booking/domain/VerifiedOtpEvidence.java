package com.localserve.booking.domain;

import com.localserve.shared.identity.PublicId;

import java.util.Objects;

public record VerifiedOtpEvidence(
        PublicId challengeId,
        PublicId bookingId,
        OtpPurpose purpose,
        long issuanceVersion,
        boolean valid,
        boolean consumed) {
    public VerifiedOtpEvidence {
        Objects.requireNonNull(challengeId, "challengeId");
        Objects.requireNonNull(bookingId, "bookingId");
        Objects.requireNonNull(purpose, "purpose");
        if (issuanceVersion < 0) {
            throw new IllegalArgumentException("issuanceVersion must be nonnegative");
        }
    }
}
