package com.localserve.phase8;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class Phase8Persistence {
    private final MongoTemplate mongo;

    public Phase8Persistence(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public ProviderProfile saveProvider(ProviderProfile profile) {
        return mongo.save(profile);
    }

    public Optional<ProviderProfile> provider(String providerId) {
        return Optional.ofNullable(mongo.findById(providerId, ProviderProfile.class));
    }

    public List<ProviderProfile> eligibleProviders(String serviceCode, String serviceZoneId) {
        Criteria criteria = Criteria.where("online").is(true)
                .and("onboardingStatus").is("APPROVED")
                .and("serviceCodes").is(serviceCode)
                .and("serviceZoneId").is(serviceZoneId);
        return mongo.find(Query.query(criteria).with(Sort.by(Sort.Direction.ASC, "providerId")), ProviderProfile.class);
    }

    public Quote insertQuote(Quote quote) {
        return mongo.insert(quote);
    }

    public Quote saveQuote(Quote quote) {
        return mongo.save(quote);
    }

    public Optional<Quote> quote(String quoteId) {
        return Optional.ofNullable(mongo.findById(quoteId, Quote.class));
    }

    public BookingView insertBookingView(BookingView view) {
        return mongo.insert(view);
    }

    public BookingView saveBookingView(BookingView view) {
        return mongo.save(view);
    }

    public Optional<BookingView> bookingView(String bookingId) {
        return Optional.ofNullable(mongo.findById(bookingId, BookingView.class));
    }

    public List<BookingView> customerBookings(String customerId) {
        return mongo.find(Query.query(Criteria.where("customerId").is(customerId))
                .with(Sort.by(Sort.Direction.DESC, "createdAt")), BookingView.class);
    }

    public List<BookingView> providerBookings(String providerId) {
        Criteria participant = new Criteria().orOperator(
                Criteria.where("selectedProviderId").is(providerId),
                Criteria.where("assignedProviderId").is(providerId));
        return mongo.find(Query.query(participant).with(Sort.by(Sort.Direction.DESC, "createdAt")),
                BookingView.class);
    }

    public Offer insertOffer(Offer offer) {
        return mongo.insert(offer);
    }

    public Offer saveOffer(Offer offer) {
        return mongo.save(offer);
    }

    public Optional<Offer> offer(String offerId) {
        return Optional.ofNullable(mongo.findById(offerId, Offer.class));
    }

    public List<Offer> bookingOffers(String bookingId) {
        return mongo.find(Query.query(Criteria.where("bookingId").is(bookingId))
                .with(Sort.by(Sort.Direction.ASC, "createdAt")), Offer.class);
    }

    public List<Offer> providerOffers(String providerId) {
        return mongo.find(Query.query(Criteria.where("providerId").is(providerId))
                .with(Sort.by(Sort.Direction.DESC, "createdAt")), Offer.class);
    }

    public LocalTestHold insertLocalTestHold(LocalTestHold hold) {
        return mongo.insert(hold);
    }

    @Document("phase8_provider_profiles")
    public static final class ProviderProfile {
        @Id public String providerId;
        @Version public Long persistenceVersion;
        public String businessDisplayName;
        public String serviceZoneId;
        public Set<String> serviceCodes = Set.of();
        public String onboardingStatus;
        public boolean online;
        public int capacity;
        public Instant updatedAt;
    }

    @Document("phase8_booking_quotes")
    public static final class Quote {
        @Id public String id;
        @Version public Long persistenceVersion;
        public String customerId;
        public String serviceId;
        public String serviceCode;
        public String serviceName;
        public String bookingType;
        public String serviceZoneId;
        public long amountMinor;
        public String currency;
        public Instant expiresAt;
        public boolean consumed;
        public String bookingId;
        public Instant createdAt;
    }

    @Document("phase8_booking_views")
    public static final class BookingView {
        @Id public String id;
        @Version public Long persistenceVersion;
        public String customerId;
        public String serviceId;
        public String serviceCode;
        public String serviceName;
        public String bookingType;
        public String serviceZoneId;
        public String address;
        public String problemDescription;
        public long expectedAmountMinor;
        public String currency;
        public String status;
        public long aggregateVersion;
        public String selectedProviderId;
        public String assignedProviderId;
        public Instant createdAt;
        public Instant updatedAt;
    }

    @Document("phase8_provider_offers")
    public static final class Offer {
        @Id public String id;
        @Version public Long persistenceVersion;
        public String bookingId;
        public String providerId;
        public String status;
        public Long estimatedAmountMinor;
        public Integer etaMinutes;
        public String note;
        public Instant expiresAt;
        public Instant createdAt;
        public Instant updatedAt;
    }

    @Document("phase8_local_test_holds")
    public static final class LocalTestHold {
        @Id public String id;
        public String bookingId;
        public String customerId;
        public long amountMinor;
        public String currency;
        public String status;
        public Instant createdAt;
    }
}
