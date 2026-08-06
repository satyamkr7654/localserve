package com.localserve.booking.infrastructure.mongo;

import com.localserve.booking.domain.BookingStatus;
import com.localserve.shared.security.ActorType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("booking_status_history")
@CompoundIndex(name = "booking_version_unique", def = "{'bookingId':1,'aggregateVersion':1}", unique = true)
public class BookingStatusHistoryDocument {
    @Id public String id;
    public String bookingId;
    public long aggregateVersion;
    public BookingStatus fromStatus;
    public BookingStatus toStatus;
    public ActorType actorType;
    public String actorId;
    public String reasonCode;
    public String correlationId;
    public Instant occurredAt;
}
