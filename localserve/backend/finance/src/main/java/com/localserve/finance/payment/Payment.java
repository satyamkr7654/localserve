package com.localserve.finance.payment;

import com.localserve.shared.error.DomainException;
import com.localserve.shared.identity.PublicId;
import com.localserve.shared.money.Money;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class Payment {
    private final PublicId id;
    private final PublicId bookingId;
    private final Money expectedAmount;
    private final Instant createdAt;
    private PaymentStatus status = PaymentStatus.CREATED;
    private String provider;
    private String providerPaymentId;
    private Money refundedAmount;
    private long version;

    public Payment(PublicId bookingId, Money expectedAmount, Clock clock) {
        this.id = PublicId.generate();
        this.bookingId = Objects.requireNonNull(bookingId, "bookingId");
        this.expectedAmount = Objects.requireNonNull(expectedAmount, "expectedAmount").requirePositive("expectedAmount");
        this.refundedAmount = Money.zero(expectedAmount.currencyCode());
        this.createdAt = Objects.requireNonNull(clock, "clock").instant();
    }

    public void markPending() { require(PaymentStatus.CREATED); status = PaymentStatus.PENDING; version++; }

    /** Called only from a verified gateway response or cryptographically verified webhook. */
    public void captureAndHold(GatewayCaptureEvidence evidence) {
        if (status != PaymentStatus.PENDING && status != PaymentStatus.AUTHORIZED) invalid();
        Objects.requireNonNull(evidence, "evidence");
        if (!evidence.signatureVerified() || !evidence.captured()) {
            throw new DomainException("PAYMENT.SERVER_VERIFICATION_REQUIRED", "Gateway capture is not verified");
        }
        if (!expectedAmount.equals(evidence.capturedAmount())) {
            throw new DomainException("PAYMENT.AMOUNT_MISMATCH", "Captured amount or currency is incorrect");
        }
        provider = evidence.provider(); providerPaymentId = evidence.providerPaymentId();
        status = PaymentStatus.HELD; version++;
    }

    public void freezeForDispute() {
        if (status != PaymentStatus.HELD && status != PaymentStatus.RELEASE_PENDING) invalid();
        status = PaymentStatus.FROZEN; version++;
    }

    public void beginRelease(boolean completionOtpVerified, boolean customerSatisfied, boolean activeDispute,
                             Instant settlementEligibleAt, Clock clock) {
        require(PaymentStatus.HELD);
        if (!completionOtpVerified || !customerSatisfied || activeDispute || clock.instant().isBefore(settlementEligibleAt)) {
            throw new DomainException("PAYMENT.RELEASE_CONDITIONS_FAILED", "Held funds are not eligible for release");
        }
        status = PaymentStatus.RELEASE_PENDING; version++;
    }

    public void markReleased() { require(PaymentStatus.RELEASE_PENDING); status = PaymentStatus.RELEASED; version++; }

    public void recordRefund(Money amount) {
        if (status != PaymentStatus.HELD && status != PaymentStatus.FROZEN
                && status != PaymentStatus.RELEASED && status != PaymentStatus.PARTIALLY_REFUNDED) invalid();
        Objects.requireNonNull(amount, "amount").requirePositive("refundAmount");
        Money total = refundedAmount.add(amount);
        if (total.compareTo(expectedAmount) > 0) throw new DomainException("PAYMENT.REFUND_EXCEEDS_CAPTURE", "Refund exceeds captured amount");
        refundedAmount = total;
        status = total.equals(expectedAmount) ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
        version++;
    }

    private void require(PaymentStatus required) { if (status != required) invalid(); }
    private static void invalid() { throw new DomainException("PAYMENT.INVALID_TRANSITION", "Payment transition is not allowed"); }
    public PublicId id() { return id; }
    public PublicId bookingId() { return bookingId; }
    public Money expectedAmount() { return expectedAmount; }
    public PaymentStatus status() { return status; }
    public Money refundedAmount() { return refundedAmount; }
}
