package com.localserve.booking.infrastructure.mongo;

import com.localserve.booking.domain.BookingStatus;
import com.localserve.booking.domain.BookingType;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("bookings")
@CompoundIndexes({
        @CompoundIndex(name = "customer_status_id", def = "{'customerId':1,'status':1,'_id':-1}"),
        @CompoundIndex(name = "provider_status_id", def = "{'assignedProviderId':1,'status':1,'_id':-1}"),
        @CompoundIndex(name = "service_status_id", def = "{'serviceId':1,'status':1,'_id':-1}")
})
public class BookingDocument {
    @Id public String id;
    @Version public Long persistenceVersion;
    public String customerId;
    public String serviceId;
    public BookingType bookingType;
    public long expectedAmountMinor;
    public String currency;
    public BookingStatus status;
    public long aggregateVersion;
    public String selectedProviderId;
    public String assignedProviderId;
    public String paymentId;
    public String activeDisputeId;
    public String startOtpChallengeId;
    public String completionOtpChallengeId;
}
