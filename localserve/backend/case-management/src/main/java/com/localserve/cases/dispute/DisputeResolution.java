package com.localserve.cases.dispute;

import com.localserve.shared.money.Money;

import java.util.Objects;

public record DisputeResolution(Money refundAmount, Money releaseAmount, String reasonCode) {
    public DisputeResolution {
        Objects.requireNonNull(refundAmount, "refundAmount").requireNonNegative("refundAmount");
        Objects.requireNonNull(releaseAmount, "releaseAmount").requireNonNegative("releaseAmount");
        refundAmount.requireSameCurrency(releaseAmount);
        if (reasonCode == null || !reasonCode.matches("[A-Z][A-Z0-9_]{2,79}")) throw new IllegalArgumentException("invalid reasonCode");
    }
}
