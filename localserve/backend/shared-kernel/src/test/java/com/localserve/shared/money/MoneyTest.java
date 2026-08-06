package com.localserve.shared.money;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {
    @Test
    void keepsMinorUnitsExactAndAssignsRemainderExplicitly() {
        Money gross = Money.of(100_001, "INR");
        Money commission = gross.portionBasisPoints(1_500);
        Money provider = gross.subtract(commission);
        assertThat(commission.amountMinor()).isEqualTo(15_000);
        assertThat(provider.amountMinor()).isEqualTo(85_001);
        assertThat(commission.add(provider)).isEqualTo(gross);
    }

    @Test
    void rejectsCurrencyMismatchAndOverflow() {
        assertThatThrownBy(() -> Money.of(100, "INR").add(Money.of(100, "USD")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of(Long.MAX_VALUE, "INR").add(Money.of(1, "INR")))
                .isInstanceOf(ArithmeticException.class);
    }
}
