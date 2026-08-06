package com.localserve.catalog.pricing;

import com.localserve.shared.identity.PublicId;
import com.localserve.shared.money.Money;

import java.time.Instant;

public record PricingQuote(PublicId quoteId, Money serviceSubtotal, Money emergencySurcharge,
                           Money tax, Money customerTotal, Money platformCommission,
                           Money providerNet, Instant expiresAt) { }
