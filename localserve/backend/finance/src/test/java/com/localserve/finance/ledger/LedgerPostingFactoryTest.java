package com.localserve.finance.ledger;

import com.localserve.shared.identity.PublicId;
import com.localserve.shared.money.Money;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerPostingFactoryTest {
    private final LedgerPostingFactory factory = new LedgerPostingFactory(
            Clock.fixed(Instant.parse("2026-08-06T12:08:00Z"), ZoneOffset.UTC));

    @Test
    void captureHoldReleaseRefundAndPayoutPostBalancedTransactions() {
        PublicId bookingId = PublicId.generate();
        PublicId paymentId = PublicId.generate();
        PublicId correlationId = PublicId.generate();
        Money gross = Money.of(100_000, "INR");

        LedgerTransaction capture = factory.captureAndHold(bookingId, paymentId, gross, correlationId);
        LedgerTransaction release = factory.releaseHeldFunds(bookingId, paymentId, gross,
                new ReleaseAllocation(Money.of(80_000, "INR"), Money.of(15_000, "INR"),
                        Money.of(3_000, "INR"), Money.of(2_000, "INR")), correlationId);
        LedgerTransaction refundReserve = factory.reserveRefund(bookingId, paymentId, Money.of(20_000, "INR"), correlationId);
        LedgerTransaction payoutReserve = factory.reservePayout(Money.of(80_000, "INR"), correlationId);

        assertThat(capture.debitTotal()).isEqualTo(gross);
        assertThat(release.debitTotal()).isEqualTo(gross);
        assertThat(refundReserve.debitTotal()).isEqualTo(Money.of(20_000, "INR"));
        assertThat(payoutReserve.debitTotal()).isEqualTo(Money.of(80_000, "INR"));
    }

    @Test
    void rejectsUnbalancedLinesAndReleaseAllocationMismatch() {
        assertThatThrownBy(() -> new LedgerTransaction(PublicId.generate(), "INVALID_POSTING",
                null, null, PublicId.generate(), Instant.now(), java.util.List.of(
                LedgerLine.debit(LedgerAccountType.SETTLEMENT_BANK_CASH, Money.of(100, "INR"), "TEST_DEBIT"),
                LedgerLine.credit(LedgerAccountType.GATEWAY_RECEIVABLE, Money.of(99, "INR"), "TEST_CREDIT")), null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> factory.releaseHeldFunds(PublicId.generate(), PublicId.generate(),
                Money.of(100, "INR"), new ReleaseAllocation(Money.of(80, "INR"), Money.of(10, "INR"),
                        Money.of(0, "INR"), Money.of(0, "INR")), PublicId.generate()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
