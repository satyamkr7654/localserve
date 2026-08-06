package com.localserve.catalog.pricing;

import com.localserve.shared.identity.PublicId;
import com.localserve.shared.money.Money;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

public final class PricingEngine {
    private final Clock clock;
    private final Duration quoteTtl;

    public PricingEngine(Clock clock, Duration quoteTtl) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.quoteTtl = Objects.requireNonNull(quoteTtl, "quoteTtl");
        if (quoteTtl.isZero() || quoteTtl.isNegative() || quoteTtl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("quoteTtl must be within one hour");
        }
    }

    public PricingQuote quote(ServicePricing pricing, int chargeableKilometres, boolean emergency) {
        Objects.requireNonNull(pricing, "pricing");
        if (chargeableKilometres < 0 || chargeableKilometres > 500) {
            throw new IllegalArgumentException("chargeableKilometres is out of range");
        }
        Money distance = new Money(Math.multiplyExact(pricing.perKilometre().amountMinor(), chargeableKilometres),
                pricing.basePrice().currency());
        Money serviceSubtotal = pricing.basePrice().add(distance);
        Money surcharge = emergency ? serviceSubtotal.portionBasisPoints(pricing.emergencySurchargeBps())
                : Money.zero(serviceSubtotal.currencyCode());
        Money commissionBase = serviceSubtotal.add(surcharge);
        Money commission = commissionBase.portionBasisPoints(pricing.commissionBps());
        Money providerNet = commissionBase.subtract(commission);
        Money tax = commissionBase.portionBasisPoints(pricing.taxBps());
        return new PricingQuote(PublicId.generate(), serviceSubtotal, surcharge, tax,
                commissionBase.add(tax), commission, providerNet, clock.instant().plus(quoteTtl));
    }
}
