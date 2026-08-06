package com.localserve.booking.infrastructure.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface BookingStatusHistoryMongoRepository extends MongoRepository<BookingStatusHistoryDocument, String> { }
