package com.localserve.booking.domain;

import com.localserve.shared.error.DomainException;
import com.localserve.shared.event.DomainEvent;
import com.localserve.shared.identity.PublicId;
import com.localserve.shared.money.Money;
import com.localserve.shared.security.Actor;
import com.localserve.shared.security.ActorType;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Booking {
    private final PublicId id;
    private final PublicId customerId;
    private final PublicId serviceId;
    private final BookingType bookingType;
    private final Money expectedPayment;
    private final Clock clock;
    private final List<DomainEvent> uncommittedEvents = new ArrayList<>();
    private BookingStatus status;
    private long version;
    private PublicId selectedProviderId;
    private PublicId assignedProviderId;
    private PublicId paymentId;
    private PublicId activeDisputeId;
    private PublicId startOtpChallengeId;
    private PublicId completionOtpChallengeId;

    private Booking(PublicId id, PublicId customerId, PublicId serviceId, BookingType bookingType,
                    Money expectedPayment, BookingStatus status, long version, Clock clock) {
        this.id = Objects.requireNonNull(id, "id");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.serviceId = Objects.requireNonNull(serviceId, "serviceId");
        this.bookingType = Objects.requireNonNull(bookingType, "bookingType");
        this.expectedPayment = Objects.requireNonNull(expectedPayment, "expectedPayment").requirePositive("expectedPayment");
        this.status = Objects.requireNonNull(status, "status");
        if (version < 0) {
            throw new IllegalArgumentException("version must be nonnegative");
        }
        this.version = version;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static Booking create(PublicId customerId, PublicId serviceId, BookingType bookingType,
                                 Money expectedPayment, Clock clock) {
        return new Booking(PublicId.generate(), customerId, serviceId, bookingType,
                expectedPayment, BookingStatus.CREATED, 0, clock);
    }

    public static Booking rehydrate(PublicId id, PublicId customerId, PublicId serviceId, BookingType bookingType,
                                    Money expectedPayment, BookingStatus status, long version,
                                    PublicId selectedProviderId, PublicId assignedProviderId,
                                    PublicId paymentId, PublicId activeDisputeId,
                                    PublicId startOtpChallengeId, PublicId completionOtpChallengeId,
                                    Clock clock) {
        Booking booking = new Booking(id, customerId, serviceId, bookingType, expectedPayment, status, version, clock);
        booking.selectedProviderId = selectedProviderId;
        booking.assignedProviderId = assignedProviderId;
        booking.paymentId = paymentId;
        booking.activeDisputeId = activeDisputeId;
        booking.startOtpChallengeId = startOtpChallengeId;
        booking.completionOtpChallengeId = completionOtpChallengeId;
        booking.validateRehydratedState();
        return booking;
    }

    public void beginProviderSearch(Actor actor, PublicId correlationId) {
        requireActorType(actor, ActorType.SYSTEM);
        transition(BookingStatus.SEARCHING_PROVIDERS, actor, correlationId, "DISPATCH_STARTED");
    }

    public void recordProvidersFound(Actor actor, PublicId correlationId) {
        requireActorType(actor, ActorType.SYSTEM);
        transition(BookingStatus.PROVIDERS_FOUND, actor, correlationId, "ELIGIBLE_OFFERS_AVAILABLE");
    }

    public void continueProviderSearch(Actor actor, PublicId correlationId) {
        requireActorType(actor, ActorType.SYSTEM);
        transition(BookingStatus.SEARCHING_PROVIDERS, actor, correlationId, "OFFER_WAVE_EXPIRED");
    }

    public void selectProvider(Actor actor, PublicId providerId, PublicId correlationId) {
        requireCustomer(actor);
        this.selectedProviderId = Objects.requireNonNull(providerId, "providerId");
        transition(BookingStatus.PROVIDER_SELECTED, actor, correlationId, "CUSTOMER_SELECTED_PROVIDER");
    }

    public void openPaymentWindow(Actor actor, PublicId correlationId) {
        requireActorType(actor, ActorType.SYSTEM);
        requirePresent(selectedProviderId, "BOOKING.OFFER_NOT_SELECTABLE", "No provider has been selected");
        transition(BookingStatus.PAYMENT_PENDING, actor, correlationId, "PAYMENT_WINDOW_OPENED");
    }

    public void recordVerifiedPayment(Actor actor, VerifiedPaymentEvidence evidence, PublicId correlationId) {
        requireActorType(actor, ActorType.SYSTEM);
        Objects.requireNonNull(evidence, "evidence");
        if (!evidence.serverVerified() || !evidence.heldInPlatformLedger()) {
            throw new DomainException("PAYMENT.SERVER_VERIFICATION_REQUIRED", "Payment is not verified and held");
        }
        if (!expectedPayment.equals(evidence.capturedAmount())) {
            throw new DomainException("PAYMENT.AMOUNT_MISMATCH", "Captured amount or currency does not match the booking");
        }
        this.paymentId = evidence.paymentId();
        transition(BookingStatus.PAYMENT_COMPLETED, actor, correlationId, "VERIFIED_PAYMENT_HELD");
    }

    public void assignSelectedProvider(Actor actor, PublicId correlationId) {
        requireActorType(actor, ActorType.SYSTEM);
        requirePresent(paymentId, "PAYMENT.SERVER_VERIFICATION_REQUIRED", "Verified payment is required before assignment");
        requirePresent(selectedProviderId, "BOOKING.OFFER_NOT_SELECTABLE", "Selected provider is missing");
        this.assignedProviderId = selectedProviderId;
        transition(BookingStatus.PROVIDER_ASSIGNED, actor, correlationId, "PAYMENT_AND_PROVIDER_CONFIRMED");
    }

    public void startJourney(Actor actor, PublicId correlationId) {
        requireAssignedProvider(actor);
        transition(BookingStatus.PROVIDER_ON_THE_WAY, actor, correlationId, "PROVIDER_STARTED_JOURNEY");
    }

    public void markArrived(Actor actor, PublicId correlationId) {
        requireAssignedProvider(actor);
        transition(BookingStatus.PROVIDER_ARRIVED, actor, correlationId, "PROVIDER_CONFIRMED_ARRIVAL");
    }

    public void requestStartOtp(Actor actor, PublicId challengeId, PublicId correlationId) {
        requireActorType(actor, ActorType.SYSTEM);
        this.startOtpChallengeId = Objects.requireNonNull(challengeId, "challengeId");
        transition(BookingStatus.START_OTP_PENDING, actor, correlationId, "START_OTP_ISSUED");
    }

    public void startService(Actor actor, VerifiedOtpEvidence evidence, PublicId correlationId) {
        requireAssignedProvider(actor);
        requireOtp(evidence, OtpPurpose.BOOKING_START, startOtpChallengeId);
        transition(BookingStatus.IN_PROGRESS, actor, correlationId, "START_OTP_VERIFIED");
    }

    public void requestCompletion(Actor actor, CompletionEvidence evidence, PublicId correlationId) {
        requireAssignedProvider(actor);
        Objects.requireNonNull(evidence, "evidence").requireSatisfied();
        transition(BookingStatus.COMPLETION_PENDING, actor, correlationId, "PROVIDER_DECLARED_COMPLETION");
    }

    public void registerCompletionOtp(Actor actor, PublicId challengeId) {
        requireActorType(actor, ActorType.SYSTEM);
        if (status != BookingStatus.COMPLETION_PENDING) {
            throw new DomainException("BOOKING.INVALID_TRANSITION", "Completion OTP cannot be issued in the current state");
        }
        this.completionOtpChallengeId = Objects.requireNonNull(challengeId, "challengeId");
    }

    public void verifyCompletion(Actor actor, VerifiedOtpEvidence evidence, PublicId correlationId) {
        requireAssignedProvider(actor);
        requireOtp(evidence, OtpPurpose.BOOKING_COMPLETION, completionOtpChallengeId);
        transition(BookingStatus.CUSTOMER_CONFIRMATION_PENDING, actor, correlationId, "COMPLETION_OTP_VERIFIED");
    }

    public void confirmSatisfaction(Actor actor, PublicId correlationId) {
        requireCustomer(actor);
        transition(BookingStatus.COMPLETED, actor, correlationId, "CUSTOMER_CONFIRMED_SATISFACTION");
    }

    public void openDispute(Actor actor, PublicId disputeId, PublicId correlationId) {
        requireParticipantOrAdmin(actor);
        this.activeDisputeId = Objects.requireNonNull(disputeId, "disputeId");
        transition(BookingStatus.DISPUTED, actor, correlationId, "DISPUTE_OPENED");
    }

    public void resolveDisputeWithRelease(Actor actor, PublicId correlationId) {
        requireAdminOrSystem(actor);
        this.activeDisputeId = null;
        transition(BookingStatus.COMPLETED, actor, correlationId, "DISPUTE_RESOLVED_RELEASE_OR_PARTIAL_REFUND");
    }

    public void markRefunded(Actor actor, PublicId correlationId) {
        requireAdminOrSystem(actor);
        this.activeDisputeId = null;
        transition(BookingStatus.REFUNDED, actor, correlationId, "VERIFIED_REFUND_COMPLETED");
    }

    public void cancel(Actor actor, String reasonCode, PublicId correlationId) {
        Objects.requireNonNull(actor, "actor");
        if (actor.type() == ActorType.PROVIDER) {
            requireAssignedProvider(actor);
        } else if (actor.type() == ActorType.CUSTOMER) {
            requireCustomer(actor);
        } else if (actor.type() != ActorType.ADMIN && actor.type() != ActorType.SYSTEM) {
            throw accessDenied();
        }
        transition(BookingStatus.CANCELLED, actor, correlationId, validateReasonCode(reasonCode));
    }

    public void close(Actor actor, ClosureEvidence evidence, PublicId correlationId) {
        requireAdminOrSystem(actor);
        Objects.requireNonNull(evidence, "evidence").requireSatisfied();
        transition(BookingStatus.CLOSED, actor, correlationId, "CLOSURE_CONDITIONS_VERIFIED");
    }

    private void requireOtp(VerifiedOtpEvidence evidence, OtpPurpose purpose, PublicId expectedChallengeId) {
        Objects.requireNonNull(evidence, "evidence");
        if (!evidence.bookingId().equals(id) || evidence.purpose() != purpose
                || !evidence.challengeId().equals(expectedChallengeId)
                || evidence.issuanceVersion() > version || !evidence.valid() || !evidence.consumed()) {
            throw new DomainException("BOOKING.OTP_PURPOSE_MISMATCH", "OTP evidence is invalid for this booking and purpose");
        }
    }

    private void transition(BookingStatus target, Actor actor, PublicId correlationId, String reasonCode) {
        Objects.requireNonNull(correlationId, "correlationId");
        BookingStateMachine.requireAllowed(status, target);
        BookingStatus previous = status;
        status = target;
        version = Math.addExact(version, 1);
        uncommittedEvents.add(new BookingStatusChanged(
                PublicId.generate(), clock.instant(), id, version, correlationId,
                previous, target, actor.type(), actor.id(), reasonCode));
    }

    private void validateRehydratedState() {
        if (status.ordinal() >= BookingStatus.PROVIDER_SELECTED.ordinal() && selectedProviderId == null
                && status != BookingStatus.CANCELLED && status != BookingStatus.REFUNDED && status != BookingStatus.CLOSED) {
            throw new IllegalArgumentException("selected provider is required for the rehydrated status");
        }
        if (status.ordinal() >= BookingStatus.PROVIDER_ASSIGNED.ordinal()
                && status.ordinal() <= BookingStatus.COMPLETED.ordinal() && assignedProviderId == null) {
            throw new IllegalArgumentException("assigned provider is required for the rehydrated status");
        }
    }

    private void requireCustomer(Actor actor) {
        requireActorType(actor, ActorType.CUSTOMER);
        if (!customerId.equals(actor.id())) {
            throw accessDenied();
        }
    }

    private void requireAssignedProvider(Actor actor) {
        requireActorType(actor, ActorType.PROVIDER);
        if (assignedProviderId == null || !assignedProviderId.equals(actor.id())) {
            throw accessDenied();
        }
    }

    private void requireParticipantOrAdmin(Actor actor) {
        Objects.requireNonNull(actor, "actor");
        boolean participant = actor.type() == ActorType.CUSTOMER && customerId.equals(actor.id())
                || actor.type() == ActorType.PROVIDER && assignedProviderId != null && assignedProviderId.equals(actor.id());
        if (!participant && actor.type() != ActorType.ADMIN) {
            throw accessDenied();
        }
    }

    private static void requireActorType(Actor actor, ActorType required) {
        Objects.requireNonNull(actor, "actor");
        if (actor.type() != required) {
            throw accessDenied();
        }
    }

    private static void requireAdminOrSystem(Actor actor) {
        Objects.requireNonNull(actor, "actor");
        if (actor.type() != ActorType.ADMIN && actor.type() != ActorType.SYSTEM) {
            throw accessDenied();
        }
    }

    private static void requirePresent(Object value, String code, String message) {
        if (value == null) {
            throw new DomainException(code, message);
        }
    }

    private static DomainException accessDenied() {
        return new DomainException("ACCESS.DENIED", "The actor is not authorized for this booking action");
    }

    private static String validateReasonCode(String reasonCode) {
        if (reasonCode == null || !reasonCode.matches("[A-Z][A-Z0-9_]{2,79}")) {
            throw new IllegalArgumentException("reasonCode has an invalid format");
        }
        return reasonCode;
    }

    public List<DomainEvent> drainUncommittedEvents() {
        List<DomainEvent> copy = List.copyOf(uncommittedEvents);
        uncommittedEvents.clear();
        return copy;
    }

    public PublicId id() { return id; }
    public PublicId customerId() { return customerId; }
    public PublicId serviceId() { return serviceId; }
    public BookingType bookingType() { return bookingType; }
    public Money expectedPayment() { return expectedPayment; }
    public BookingStatus status() { return status; }
    public long version() { return version; }
    public PublicId selectedProviderId() { return selectedProviderId; }
    public PublicId assignedProviderId() { return assignedProviderId; }
    public PublicId paymentId() { return paymentId; }
    public PublicId activeDisputeId() { return activeDisputeId; }
    public PublicId startOtpChallengeId() { return startOtpChallengeId; }
    public PublicId completionOtpChallengeId() { return completionOtpChallengeId; }
}
