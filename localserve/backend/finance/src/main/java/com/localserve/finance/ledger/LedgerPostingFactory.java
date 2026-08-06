package com.localserve.finance.ledger;

import com.localserve.shared.identity.PublicId;
import com.localserve.shared.money.Money;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LedgerPostingFactory {
    private final Clock clock;

    public LedgerPostingFactory(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public LedgerTransaction captureAndHold(PublicId bookingId, PublicId paymentId, Money amount, PublicId correlationId) {
        amount.requirePositive("captured amount");
        return transaction("PAYMENT_CAPTURED_AND_HELD", bookingId, paymentId, correlationId, List.of(
                LedgerLine.debit(LedgerAccountType.GATEWAY_RECEIVABLE, amount, "GATEWAY_PAYMENT_CAPTURED"),
                LedgerLine.credit(LedgerAccountType.CUSTOMER_HELD_FUNDS, amount, "CUSTOMER_FUNDS_HELD")));
    }

    public LedgerTransaction recordGatewaySettlement(PublicId paymentId, Money amount, PublicId correlationId) {
        amount.requirePositive("settled amount");
        return transaction("GATEWAY_SETTLEMENT_RECORDED", null, paymentId, correlationId, List.of(
                LedgerLine.debit(LedgerAccountType.SETTLEMENT_BANK_CASH, amount, "SETTLEMENT_CASH_RECEIVED"),
                LedgerLine.credit(LedgerAccountType.GATEWAY_RECEIVABLE, amount, "GATEWAY_RECEIVABLE_CLEARED")));
    }

    public LedgerTransaction releaseHeldFunds(PublicId bookingId, PublicId paymentId, Money held,
                                               ReleaseAllocation allocation, PublicId correlationId) {
        held.requirePositive("held amount");
        if (!held.equals(allocation.total())) {
            throw new IllegalArgumentException("release allocation must equal held amount and currency");
        }
        List<LedgerLine> lines = new ArrayList<>();
        lines.add(LedgerLine.debit(LedgerAccountType.CUSTOMER_HELD_FUNDS, held, "CUSTOMER_HELD_FUNDS_RELEASED"));
        addCreditIfPositive(lines, LedgerAccountType.PROVIDER_AVAILABLE_PAYABLE, allocation.provider(), "PROVIDER_WALLET_CREDITED");
        addCreditIfPositive(lines, LedgerAccountType.COMMISSION_REVENUE, allocation.commission(), "PLATFORM_COMMISSION_RECOGNIZED");
        addCreditIfPositive(lines, LedgerAccountType.TAX_PAYABLE, allocation.tax(), "TAX_LIABILITY_RECOGNIZED");
        addCreditIfPositive(lines, LedgerAccountType.CONVENIENCE_FEE_REVENUE, allocation.convenienceFee(), "CONVENIENCE_FEE_RECOGNIZED");
        return transaction("HELD_FUNDS_RELEASED", bookingId, paymentId, correlationId, lines);
    }

    public LedgerTransaction freezeProviderFunds(PublicId bookingId, Money amount, PublicId correlationId) {
        return move("PROVIDER_FUNDS_FROZEN", bookingId, null, amount, correlationId,
                LedgerAccountType.PROVIDER_AVAILABLE_PAYABLE, LedgerAccountType.PROVIDER_FROZEN_PAYABLE,
                "AVAILABLE_PROVIDER_FUNDS_FROZEN");
    }

    public LedgerTransaction unfreezeProviderFunds(PublicId bookingId, Money amount, PublicId correlationId) {
        return move("PROVIDER_FUNDS_UNFROZEN", bookingId, null, amount, correlationId,
                LedgerAccountType.PROVIDER_FROZEN_PAYABLE, LedgerAccountType.PROVIDER_AVAILABLE_PAYABLE,
                "FROZEN_PROVIDER_FUNDS_RELEASED");
    }

    public LedgerTransaction reserveRefund(PublicId bookingId, PublicId paymentId, Money amount, PublicId correlationId) {
        return move("REFUND_RESERVED", bookingId, paymentId, amount, correlationId,
                LedgerAccountType.CUSTOMER_HELD_FUNDS, LedgerAccountType.CUSTOMER_REFUND_PAYABLE,
                "CUSTOMER_REFUND_RESERVED");
    }

    public LedgerTransaction settleRefund(PublicId bookingId, PublicId paymentId, Money amount, PublicId correlationId) {
        return move("REFUND_SETTLED", bookingId, paymentId, amount, correlationId,
                LedgerAccountType.CUSTOMER_REFUND_PAYABLE, LedgerAccountType.SETTLEMENT_BANK_CASH,
                "CUSTOMER_REFUND_PAID");
    }

    public LedgerTransaction reservePayout(Money amount, PublicId correlationId) {
        return move("PAYOUT_RESERVED", null, null, amount, correlationId,
                LedgerAccountType.PROVIDER_AVAILABLE_PAYABLE, LedgerAccountType.PAYOUT_CLEARING_PAYABLE,
                "PROVIDER_PAYOUT_RESERVED");
    }

    public LedgerTransaction settlePayout(Money amount, PublicId correlationId) {
        return move("PAYOUT_SETTLED", null, null, amount, correlationId,
                LedgerAccountType.PAYOUT_CLEARING_PAYABLE, LedgerAccountType.SETTLEMENT_BANK_CASH,
                "PROVIDER_PAYOUT_PAID");
    }

    public LedgerTransaction reversePayout(Money amount, PublicId correlationId, PublicId originalTransactionId) {
        LedgerTransaction originalShape = new LedgerTransaction(PublicId.generate(), "PAYOUT_REVERSED",
                null, null, correlationId, clock.instant(), List.of(
                LedgerLine.debit(LedgerAccountType.SETTLEMENT_BANK_CASH, amount, "PAYOUT_REVERSAL_RECEIVED"),
                LedgerLine.credit(LedgerAccountType.PROVIDER_AVAILABLE_PAYABLE, amount, "PROVIDER_BALANCE_RESTORED")),
                Objects.requireNonNull(originalTransactionId, "originalTransactionId"));
        return originalShape;
    }

    public LedgerTransaction issuePromotionalCredit(Money amount, PublicId correlationId) {
        return move("PROMOTIONAL_CREDIT_ISSUED", null, null, amount, correlationId,
                LedgerAccountType.PROMOTION_EXPENSE, LedgerAccountType.PROMO_CREDIT_LIABILITY,
                "PROMOTIONAL_CREDIT_ISSUED");
    }

    public LedgerTransaction redeemPromotionalCredit(PublicId bookingId, Money amount, PublicId correlationId) {
        return move("PROMOTIONAL_CREDIT_REDEEMED", bookingId, null, amount, correlationId,
                LedgerAccountType.PROMO_CREDIT_LIABILITY, LedgerAccountType.CUSTOMER_HELD_FUNDS,
                "PROMOTIONAL_CREDIT_APPLIED_TO_BOOKING");
    }

    public LedgerTransaction recordGatewayFee(PublicId paymentId, Money amount, PublicId correlationId) {
        return move("GATEWAY_FEE_RECORDED", null, paymentId, amount, correlationId,
                LedgerAccountType.GATEWAY_FEE_EXPENSE, LedgerAccountType.SETTLEMENT_BANK_CASH,
                "GATEWAY_PROCESSING_FEE_PAID");
    }

    private LedgerTransaction move(String type, PublicId bookingId, PublicId paymentId, Money amount,
                                   PublicId correlationId, LedgerAccountType debit, LedgerAccountType credit,
                                   String memo) {
        amount.requirePositive("ledger amount");
        return transaction(type, bookingId, paymentId, correlationId, List.of(
                LedgerLine.debit(debit, amount, memo),
                LedgerLine.credit(credit, amount, memo)));
    }

    private LedgerTransaction transaction(String type, PublicId bookingId, PublicId paymentId,
                                          PublicId correlationId, List<LedgerLine> lines) {
        return new LedgerTransaction(PublicId.generate(), type, bookingId, paymentId,
                correlationId, clock.instant(), lines, null);
    }

    private static void addCreditIfPositive(List<LedgerLine> lines, LedgerAccountType account,
                                            Money amount, String memo) {
        if (amount.amountMinor() > 0) {
            lines.add(LedgerLine.credit(account, amount, memo));
        }
    }
}
