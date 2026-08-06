package com.localserve.booking.infrastructure.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document("outbox_events")
@CompoundIndex(name = "outbox_dispatch", def = "{'publishedAt':1,'nextAttemptAt':1,'claimUntil':1,'occurredAt':1}")
public class OutboxDocument {
    @Id public String id;
    @Indexed(unique = true) public String eventId;
    public String eventType;
    public String aggregateType;
    public String aggregateId;
    public long aggregateVersion;
    public String correlationId;
    public Instant occurredAt;
    public Instant nextAttemptAt;
    public Instant publishedAt;
    public String claimedBy;
    public Instant claimUntil;
    public String lastErrorCode;
    public int attempts;
    public Map<String, String> payload;
}
