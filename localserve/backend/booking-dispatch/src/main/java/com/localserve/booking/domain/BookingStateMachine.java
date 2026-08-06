package com.localserve.booking.domain;

import com.localserve.shared.error.DomainException;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public final class BookingStateMachine {
    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED = buildAllowedTransitions();

    private BookingStateMachine() {
    }

    public static boolean allows(BookingStatus from, BookingStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static Set<BookingStatus> allowedTargets(BookingStatus from) {
        return ALLOWED.getOrDefault(from, Set.of());
    }

    public static void requireAllowed(BookingStatus from, BookingStatus to) {
        if (!allows(from, to)) {
            throw new DomainException(
                    "BOOKING.INVALID_TRANSITION",
                    "Booking transition is not allowed",
                    Map.of("currentState", from.name(), "requestedState", to.name()));
        }
    }

    private static Map<BookingStatus, Set<BookingStatus>> buildAllowedTransitions() {
        EnumMap<BookingStatus, Set<BookingStatus>> transitions = new EnumMap<>(BookingStatus.class);
        transitions.put(BookingStatus.CREATED, Set.of(BookingStatus.SEARCHING_PROVIDERS, BookingStatus.CANCELLED));
        transitions.put(BookingStatus.SEARCHING_PROVIDERS, Set.of(BookingStatus.PROVIDERS_FOUND, BookingStatus.CANCELLED));
        transitions.put(BookingStatus.PROVIDERS_FOUND, Set.of(BookingStatus.PROVIDER_SELECTED, BookingStatus.SEARCHING_PROVIDERS, BookingStatus.CANCELLED));
        transitions.put(BookingStatus.PROVIDER_SELECTED, Set.of(BookingStatus.PAYMENT_PENDING, BookingStatus.CANCELLED));
        transitions.put(BookingStatus.PAYMENT_PENDING, Set.of(BookingStatus.PAYMENT_COMPLETED, BookingStatus.CANCELLED));
        transitions.put(BookingStatus.PAYMENT_COMPLETED, Set.of(BookingStatus.PROVIDER_ASSIGNED, BookingStatus.REFUNDED, BookingStatus.DISPUTED));
        transitions.put(BookingStatus.PROVIDER_ASSIGNED, Set.of(BookingStatus.PROVIDER_ON_THE_WAY, BookingStatus.CANCELLED, BookingStatus.DISPUTED));
        transitions.put(BookingStatus.PROVIDER_ON_THE_WAY, Set.of(BookingStatus.PROVIDER_ARRIVED, BookingStatus.CANCELLED, BookingStatus.DISPUTED));
        transitions.put(BookingStatus.PROVIDER_ARRIVED, Set.of(BookingStatus.START_OTP_PENDING, BookingStatus.CANCELLED, BookingStatus.DISPUTED));
        transitions.put(BookingStatus.START_OTP_PENDING, Set.of(BookingStatus.IN_PROGRESS, BookingStatus.CANCELLED, BookingStatus.DISPUTED));
        transitions.put(BookingStatus.IN_PROGRESS, Set.of(BookingStatus.COMPLETION_PENDING, BookingStatus.DISPUTED));
        transitions.put(BookingStatus.COMPLETION_PENDING, Set.of(BookingStatus.CUSTOMER_CONFIRMATION_PENDING, BookingStatus.DISPUTED));
        transitions.put(BookingStatus.CUSTOMER_CONFIRMATION_PENDING, Set.of(BookingStatus.COMPLETED, BookingStatus.DISPUTED));
        transitions.put(BookingStatus.COMPLETED, Set.of(BookingStatus.CLOSED, BookingStatus.DISPUTED));
        transitions.put(BookingStatus.DISPUTED, Set.of(BookingStatus.COMPLETED, BookingStatus.REFUNDED));
        transitions.put(BookingStatus.CANCELLED, Set.of(BookingStatus.REFUNDED, BookingStatus.CLOSED));
        transitions.put(BookingStatus.REFUNDED, Set.of(BookingStatus.CLOSED));
        transitions.put(BookingStatus.CLOSED, Set.of());
        return Map.copyOf(transitions);
    }
}
