package com.localserve.booking.infrastructure.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SpringDataBookingMongoRepository extends MongoRepository<BookingDocument, String> { }
