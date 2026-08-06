package com.localserve.booking.application;

import com.localserve.booking.domain.Booking;
import com.localserve.shared.identity.PublicId;

import java.util.Optional;

public interface BookingRepository {
    Optional<Booking> findById(PublicId id);
    Booking save(Booking booking);
}
