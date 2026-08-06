package com.localserve.reputation.review;

import com.localserve.shared.error.DomainException;
import com.localserve.shared.identity.PublicId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ReviewTest {
    @Test void onlyClosedBookingCanCreateOneVerifiedReview() {
        assertThrows(DomainException.class, () -> Review.createVerified(PublicId.generate(), PublicId.generate(),
                PublicId.generate(), 5, "Good", List.of(), false, false, Clock.systemUTC()));
        assertThrows(DomainException.class, () -> Review.createVerified(PublicId.generate(), PublicId.generate(),
                PublicId.generate(), 5, "Good", List.of(), true, true, Clock.systemUTC()));
    }
}
