package com.localserve.booking.domain;

import com.localserve.shared.identity.PublicId;
import com.localserve.shared.money.Money;

import java.util.Objects;

public record VerifiedPaymentEvidence(
        PublicId paymentId,
        Money capturedAmount,
        boolean serverVerified,
        boolean heldInPlatformLedger) {
    public VerifiedPaymentEvidence {
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(capturedAmount, "capturedAmount").requirePositive("capturedAmount");
    }
}
