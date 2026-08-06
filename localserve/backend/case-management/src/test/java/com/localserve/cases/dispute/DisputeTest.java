package com.localserve.cases.dispute;

import com.localserve.shared.error.DomainException;
import com.localserve.shared.identity.PublicId;
import com.localserve.shared.money.Money;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;

class DisputeTest {
    @Test void resolutionMustAllocateEveryHeldMinorUnit() {
        var dispute = new Dispute(PublicId.generate(), PublicId.generate(), Money.of(10_001, "INR"), Clock.systemUTC());
        dispute.beginReview(PublicId.generate());
        assertThrows(DomainException.class, () -> dispute.resolve(PublicId.generate(),
                new DisputeResolution(Money.of(5_000, "INR"), Money.of(5_000, "INR"), "PARTIAL_REFUND")));
        dispute.resolve(PublicId.generate(), new DisputeResolution(Money.of(5_000, "INR"),
                Money.of(5_001, "INR"), "PARTIAL_REFUND"));
        assertEquals(DisputeStatus.RESOLVED_PARTIAL_REFUND, dispute.status());
    }
}
