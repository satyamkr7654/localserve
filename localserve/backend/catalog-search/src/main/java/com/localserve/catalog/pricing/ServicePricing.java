package com.localserve.catalog.pricing;

import com.localserve.shared.money.Money;

import java.util.Objects;

public record ServicePricing(Money basePrice, Money perKilometre, int emergencySurchargeBps,
                             int commissionBps, int taxBps) {
    public ServicePricing {
        Objects.requireNonNull(basePrice, "basePrice").requireNonNegative("basePrice");
        Objects.requireNonNull(perKilometre, "perKilometre").requireNonNegative("perKilometre");
        basePrice.requireSameCurrency(perKilometre);
        requireBps(emergencySurchargeBps, "emergencySurchargeBps");
        requireBps(commissionBps, "commissionBps");
        requireBps(taxBps, "taxBps");
    }

    private static void requireBps(int value, String field) {
        if (value < 0 || value > 10_000) throw new IllegalArgumentException(field + " must be between 0 and 10000");
    }
}
