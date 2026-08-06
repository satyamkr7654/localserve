package com.localserve.booking.infrastructure.mongo;

import com.localserve.booking.application.BookingRepository;
import com.localserve.booking.domain.Booking;
import com.localserve.shared.identity.PublicId;
import com.localserve.shared.money.Money;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.Optional;

@Repository
public class MongoBookingRepository implements BookingRepository {
    private final SpringDataBookingMongoRepository repository;
    private final Clock clock;

    public MongoBookingRepository(SpringDataBookingMongoRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override public Optional<Booking> findById(PublicId id) { return repository.findById(id.toString()).map(this::toDomain); }

    @Override public Booking save(Booking booking) {
        BookingDocument document = toDocument(booking);
        repository.findById(document.id).ifPresent(existing -> document.persistenceVersion = existing.persistenceVersion);
        return toDomain(repository.save(document));
    }

    private BookingDocument toDocument(Booking source) {
        var target = new BookingDocument();
        target.id = source.id().toString();
        target.customerId = source.customerId().toString();
        target.serviceId = source.serviceId().toString();
        target.bookingType = source.bookingType();
        target.expectedAmountMinor = source.expectedPayment().amountMinor();
        target.currency = source.expectedPayment().currencyCode();
        target.status = source.status();
        target.aggregateVersion = source.version();
        target.selectedProviderId = string(source.selectedProviderId());
        target.assignedProviderId = string(source.assignedProviderId());
        target.paymentId = string(source.paymentId());
        target.activeDisputeId = string(source.activeDisputeId());
        target.startOtpChallengeId = string(source.startOtpChallengeId());
        target.completionOtpChallengeId = string(source.completionOtpChallengeId());
        return target;
    }

    private Booking toDomain(BookingDocument source) {
        return Booking.rehydrate(PublicId.parse(source.id), PublicId.parse(source.customerId),
                PublicId.parse(source.serviceId), source.bookingType, Money.of(source.expectedAmountMinor, source.currency),
                source.status, source.aggregateVersion, id(source.selectedProviderId), id(source.assignedProviderId),
                id(source.paymentId), id(source.activeDisputeId), id(source.startOtpChallengeId),
                id(source.completionOtpChallengeId), clock);
    }

    private static String string(PublicId id) { return id == null ? null : id.toString(); }
    private static PublicId id(String value) { return value == null ? null : PublicId.parse(value); }
}
