package com.localserve.finance.ledger;

import com.localserve.shared.money.Money;

import java.util.Objects;

public record ReleaseAllocation(Money provider, Money commission, Money tax, Money convenienceFee) {
    public ReleaseAllocation {
        Objects.requireNonNull(provider, "provider").requireNonNegative("provider");
        Objects.requireNonNull(commission, "commission").requireNonNegative("commission");
        Objects.requireNonNull(tax, "tax").requireNonNegative("tax");
        Objects.requireNonNull(convenienceFee, "convenienceFee").requireNonNegative("convenienceFee");
        provider.requireSameCurrency(commission);
        provider.requireSameCurrency(tax);
        provider.requireSameCurrency(convenienceFee);
    }

    public Money total() {
        return provider.add(commission).add(tax).add(convenienceFee);
    }
}
