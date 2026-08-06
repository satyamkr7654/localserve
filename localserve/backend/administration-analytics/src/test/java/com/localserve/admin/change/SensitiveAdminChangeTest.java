package com.localserve.admin.change;

import com.localserve.shared.error.DomainException;
import com.localserve.shared.identity.PublicId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveAdminChangeTest {
    @Test void makerCannotApproveOwnChange() {
        PublicId maker = PublicId.generate();
        var change = new SensitiveAdminChange(maker, "COMMISSION_CHANGE", Map.of("commissionBps", "1500"),
                Clock.systemUTC(), Duration.ofHours(1));
        assertThrows(DomainException.class, () -> change.approve(maker, Clock.systemUTC()));
        change.approve(PublicId.generate(), Clock.systemUTC());
        assertEquals(AdminChangeStatus.APPROVED, change.status());
    }
}
