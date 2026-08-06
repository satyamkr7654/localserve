package com.localserve.finance.ledger;

import com.localserve.shared.identity.PublicId;
import com.localserve.shared.money.Money;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record LedgerTransaction(
        PublicId id,
        String transactionType,
        PublicId bookingId,
        PublicId paymentId,
        PublicId correlationId,
        Instant occurredAt,
        List<LedgerLine> lines,
        PublicId reversalOfTransactionId) {

    public LedgerTransaction {
        Objects.requireNonNull(id, "id");
        if (transactionType == null || !transactionType.matches("[A-Z][A-Z0-9_]{2,79}")) {
            throw new IllegalArgumentException("transactionType has an invalid format");
        }
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        if (lines.size() < 2 || lines.size() > 20) {
            throw new IllegalArgumentException("ledger transaction must have 2-20 lines");
        }
        requireBalanced(lines);
    }

    private static void requireBalanced(List<LedgerLine> lines) {
        String currency = lines.get(0).amount().currencyCode();
        long debits = 0;
        long credits = 0;
        for (LedgerLine line : lines) {
            if (!currency.equals(line.amount().currencyCode())) {
                throw new IllegalArgumentException("all ledger lines must use one currency");
            }
            if (line.side() == LedgerSide.DEBIT) {
                debits = Math.addExact(debits, line.amount().amountMinor());
            } else {
                credits = Math.addExact(credits, line.amount().amountMinor());
            }
        }
        if (debits != credits) {
            throw new IllegalArgumentException("ledger transaction is not balanced");
        }
    }

    public Money debitTotal() {
        LedgerLine first = lines.get(0);
        long value = lines.stream().filter(line -> line.side() == LedgerSide.DEBIT)
                .mapToLong(line -> line.amount().amountMinor()).reduce(0, Math::addExact);
        return Money.of(value, first.amount().currencyCode());
    }
}
