package com.localserve.shared.event;

import com.localserve.shared.identity.PublicId;

import java.time.Instant;

public interface DomainEvent {
    PublicId eventId();
    String eventType();
    Instant occurredAt();
    String aggregateType();
    PublicId aggregateId();
    long aggregateVersion();
    PublicId correlationId();
}
