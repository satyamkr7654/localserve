package com.localserve.finance.payment;

import com.localserve.shared.money.Money;

import java.time.Instant;
import java.util.Objects;

public record GatewayCaptureEvidence(String provider, String providerPaymentId, Money capturedAmount,
                                     boolean signatureVerified, boolean captured, Instant verifiedAt) {
    public GatewayCaptureEvidence {
        if (provider == null || !provider.matches("RAZORPAY|STRIPE")) throw new IllegalArgumentException("unsupported provider");
        if (providerPaymentId == null || providerPaymentId.isBlank() || providerPaymentId.length() > 255) throw new IllegalArgumentException("invalid providerPaymentId");
        Objects.requireNonNull(capturedAmount, "capturedAmount").requirePositive("capturedAmount");
        Objects.requireNonNull(verifiedAt, "verifiedAt");
    }
}
