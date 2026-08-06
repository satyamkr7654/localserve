package com.localserve.booking.domain;

import com.localserve.shared.event.DomainEvent;
import com.localserve.shared.identity.PublicId;
import com.localserve.shared.security.ActorType;

import java.time.Instant;
import java.util.Objects;

public record BookingStatusChanged(
        PublicId eventId,
        Instant occurredAt,
        PublicId aggregateId,
        long aggregateVersion,
        PublicId correlationId,
        BookingStatus fromStatus,
        BookingStatus toStatus,
        ActorType actorType,
        PublicId actorId,
        String reasonCode) implements DomainEvent {
    public BookingStatusChanged {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(fromStatus, "fromStatus");
        Objects.requireNonNull(toStatus, "toStatus");
        Objects.requireNonNull(actorType, "actorType");
        Objects.requireNonNull(reasonCode, "reasonCode");
    }

    @Override
    public String eventType() {
        return "localserve.booking.booking-status-changed.v1";
    }

    @Override
    public String aggregateType() {
        return "BOOKING";
    }
}
