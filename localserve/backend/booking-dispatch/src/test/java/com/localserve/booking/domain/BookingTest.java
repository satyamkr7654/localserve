package com.localserve.booking.domain;

import com.localserve.shared.error.DomainException;
import com.localserve.shared.identity.PublicId;
import com.localserve.shared.money.Money;
import com.localserve.shared.security.Actor;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingTest {
    private final PublicId customerId = PublicId.generate();
    private final PublicId providerId = PublicId.generate();
    private final PublicId correlationId = PublicId.generate();
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-06T12:08:00Z"), ZoneOffset.UTC);

    @Test
    void completesTheCanonicalHappyPathOnlyWithVerifiedPaymentAndSeparateOtps() {
        Booking booking = createBooking();
        booking.beginProviderSearch(Actor.system(), correlationId);
        booking.recordProvidersFound(Actor.system(), correlationId);
        booking.selectProvider(Actor.customer(customerId), providerId, correlationId);
        booking.openPaymentWindow(Actor.system(), correlationId);
        booking.recordVerifiedPayment(Actor.system(),
                new VerifiedPaymentEvidence(PublicId.generate(), Money.of(100_000, "INR"), true, true), correlationId);
        booking.assignSelectedProvider(Actor.system(), correlationId);
        booking.startJourney(Actor.provider(providerId), correlationId);
        booking.markArrived(Actor.provider(providerId), correlationId);
        PublicId startChallenge = PublicId.generate();
        booking.requestStartOtp(Actor.system(), startChallenge, correlationId);
        booking.startService(Actor.provider(providerId),
                new VerifiedOtpEvidence(startChallenge, booking.id(), OtpPurpose.BOOKING_START,
                        booking.version(), true, true), correlationId);
        booking.requestCompletion(Actor.provider(providerId), new CompletionEvidence(true, true), correlationId);
        PublicId completionChallenge = PublicId.generate();
        booking.registerCompletionOtp(Actor.system(), completionChallenge);
        booking.verifyCompletion(Actor.provider(providerId),
                new VerifiedOtpEvidence(completionChallenge, booking.id(), OtpPurpose.BOOKING_COMPLETION,
                        booking.version(), true, true), correlationId);
        booking.confirmSatisfaction(Actor.customer(customerId), correlationId);
        booking.close(Actor.system(), new ClosureEvidence(true, true, true, true), correlationId);

        assertThat(booking.status()).isEqualTo(BookingStatus.CLOSED);
        assertThat(booking.version()).isEqualTo(14);
        assertThat(booking.drainUncommittedEvents()).hasSize(14);
    }

    @Test
    void rejectsAssignmentWithoutVerifiedHeldPayment() {
        Booking booking = createBooking();
        booking.beginProviderSearch(Actor.system(), correlationId);
        booking.recordProvidersFound(Actor.system(), correlationId);
        booking.selectProvider(Actor.customer(customerId), providerId, correlationId);
        booking.openPaymentWindow(Actor.system(), correlationId);

        assertThatThrownBy(() -> booking.assignSelectedProvider(Actor.system(), correlationId))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo("PAYMENT.SERVER_VERIFICATION_REQUIRED");
    }

    @Test
    void rejectsWrongProviderAndCrossPurposeOtp() {
        Booking booking = paidAssignedAndArrived();
        PublicId startChallenge = PublicId.generate();
        booking.requestStartOtp(Actor.system(), startChallenge, correlationId);

        assertThatThrownBy(() -> booking.startService(Actor.provider(PublicId.generate()),
                new VerifiedOtpEvidence(startChallenge, booking.id(), OtpPurpose.BOOKING_START,
                        booking.version(), true, true), correlationId))
                .isInstanceOf(DomainException.class);

        assertThatThrownBy(() -> booking.startService(Actor.provider(providerId),
                new VerifiedOtpEvidence(startChallenge, booking.id(), OtpPurpose.BOOKING_COMPLETION,
                        booking.version(), true, true), correlationId))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo("BOOKING.OTP_PURPOSE_MISMATCH");
    }

    @Test
    void rejectsSkippingCanonicalStatesAndUnsafeClose() {
        Booking booking = createBooking();
        assertThatThrownBy(() -> booking.confirmSatisfaction(Actor.customer(customerId), correlationId))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo("BOOKING.INVALID_TRANSITION");

        Booking cancelled = createBooking();
        cancelled.cancel(Actor.customer(customerId), "CUSTOMER_CHANGED_MIND", correlationId);
        assertThatThrownBy(() -> cancelled.close(Actor.system(),
                new ClosureEvidence(true, false, true, true), correlationId))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo("BOOKING.CLOSURE_CONDITIONS_NOT_MET");
    }

    private Booking paidAssignedAndArrived() {
        Booking booking = createBooking();
        booking.beginProviderSearch(Actor.system(), correlationId);
        booking.recordProvidersFound(Actor.system(), correlationId);
        booking.selectProvider(Actor.customer(customerId), providerId, correlationId);
        booking.openPaymentWindow(Actor.system(), correlationId);
        booking.recordVerifiedPayment(Actor.system(),
                new VerifiedPaymentEvidence(PublicId.generate(), Money.of(100_000, "INR"), true, true), correlationId);
        booking.assignSelectedProvider(Actor.system(), correlationId);
        booking.startJourney(Actor.provider(providerId), correlationId);
        booking.markArrived(Actor.provider(providerId), correlationId);
        return booking;
    }

    private Booking createBooking() {
        return Booking.create(customerId, PublicId.generate(), BookingType.INSTANT, Money.of(100_000, "INR"), clock);
    }
}
