package com.localserve.finance.payment;

import com.localserve.shared.error.DomainException;
import com.localserve.shared.identity.PublicId;
import com.localserve.shared.money.Money;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PaymentTest {
    @Test void neverTrustsUnverifiedOrMismatchedCapture() {
        var payment = new Payment(PublicId.generate(), Money.of(10_000, "INR"), Clock.systemUTC());
        payment.markPending();
        assertThrows(DomainException.class, () -> payment.captureAndHold(new GatewayCaptureEvidence(
                "RAZORPAY", "pay_1", Money.of(10_000, "INR"), false, true, Instant.now())));
        assertThrows(DomainException.class, () -> payment.captureAndHold(new GatewayCaptureEvidence(
                "RAZORPAY", "pay_1", Money.of(9_999, "INR"), true, true, Instant.now())));
    }

    @Test void releaseRequiresEveryEscrowLikeCondition() {
        var payment = new Payment(PublicId.generate(), Money.of(10_000, "INR"), Clock.systemUTC());
        payment.markPending();
        payment.captureAndHold(new GatewayCaptureEvidence("STRIPE", "pi_1", Money.of(10_000, "INR"), true, true, Instant.now()));
        assertThrows(DomainException.class, () -> payment.beginRelease(true, true, true, Instant.EPOCH, Clock.systemUTC()));
        payment.beginRelease(true, true, false, Instant.EPOCH, Clock.systemUTC());
        payment.markReleased();
        assertEquals(PaymentStatus.RELEASED, payment.status());
    }
}
