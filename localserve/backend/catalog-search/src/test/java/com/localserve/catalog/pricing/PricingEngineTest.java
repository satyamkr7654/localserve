package com.localserve.catalog.pricing;

import com.localserve.shared.money.Money;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PricingEngineTest {
    @Test void usesMinorUnitsAndAssignsDeterministicRounding() {
        var pricing = new ServicePricing(Money.of(10_000, "INR"), Money.of(500, "INR"), 2_000, 1_500, 1_800);
        var quote = new PricingEngine(Clock.systemUTC(), Duration.ofMinutes(10)).quote(pricing, 3, true);
        assertEquals(11_500, quote.serviceSubtotal().amountMinor());
        assertEquals(2_300, quote.emergencySurcharge().amountMinor());
        assertEquals(2_484, quote.tax().amountMinor());
        assertEquals(16_284, quote.customerTotal().amountMinor());
    }
}
