package com.localserve.booking.application;

import com.localserve.booking.domain.Booking;
import com.localserve.booking.domain.BookingStatusChanged;
import com.localserve.booking.infrastructure.mongo.BookingStatusHistoryDocument;
import com.localserve.booking.infrastructure.mongo.BookingStatusHistoryMongoRepository;
import com.localserve.booking.infrastructure.mongo.OutboxDocument;
import com.localserve.booking.infrastructure.mongo.OutboxMongoRepository;
import com.localserve.shared.event.DomainEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class BookingPersistenceService {
    private final BookingRepository bookings;
    private final BookingStatusHistoryMongoRepository history;
    private final OutboxMongoRepository outbox;

    public BookingPersistenceService(BookingRepository bookings, BookingStatusHistoryMongoRepository history,
                                     OutboxMongoRepository outbox) {
        this.bookings = bookings;
        this.history = history;
        this.outbox = outbox;
    }

    /** Requires MongoDB replica-set transactions in every non-test environment. */
    @Transactional
    public Booking save(Booking booking) {
        Booking saved = bookings.save(booking);
        for (DomainEvent event : booking.drainUncommittedEvents()) {
            if (event instanceof BookingStatusChanged changed) history.save(history(changed));
            outbox.save(outbox(event));
        }
        return saved;
    }

    private static BookingStatusHistoryDocument history(BookingStatusChanged event) {
        var document = new BookingStatusHistoryDocument();
        document.id = event.eventId().toString(); document.bookingId = event.aggregateId().toString();
        document.aggregateVersion = event.aggregateVersion(); document.fromStatus = event.fromStatus();
        document.toStatus = event.toStatus(); document.actorType = event.actorType();
        document.actorId = event.actorId() == null ? null : event.actorId().toString();
        document.reasonCode = event.reasonCode(); document.correlationId = event.correlationId().toString();
        document.occurredAt = event.occurredAt();
        return document;
    }

    private static OutboxDocument outbox(DomainEvent event) {
        var document = new OutboxDocument();
        document.id = event.eventId().toString(); document.eventId = event.eventId().toString();
        document.eventType = event.eventType(); document.aggregateType = event.aggregateType();
        document.aggregateId = event.aggregateId().toString(); document.aggregateVersion = event.aggregateVersion();
        document.correlationId = event.correlationId().toString(); document.occurredAt = event.occurredAt();
        document.nextAttemptAt = event.occurredAt();
        document.payload = event instanceof BookingStatusChanged changed
                ? Map.of("schemaVersion", "1", "bookingId", changed.aggregateId().toString(),
                    "fromStatus", changed.fromStatus().name(), "toStatus", changed.toStatus().name(),
                    "reasonCode", changed.reasonCode())
                : Map.of("schemaVersion", "1");
        return document;
    }
}
