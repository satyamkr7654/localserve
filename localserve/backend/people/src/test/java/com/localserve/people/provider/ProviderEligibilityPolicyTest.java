package com.localserve.people.provider;

import com.localserve.shared.identity.PublicId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProviderEligibilityPolicyTest {
    private final Instant now = Instant.parse("2026-08-06T10:00:00Z");
    private final ProviderEligibilityPolicy policy = new ProviderEligibilityPolicy(
            Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(2), 1);

    @Test void approvesOnlyFreshVerifiedProviderWithSkillAndCapacity() {
        PublicId service = PublicId.generate();
        var snapshot = new ProviderEligibilitySnapshot(PublicId.generate(), ProviderVerificationStatus.APPROVED,
                true, true, true, now.minusSeconds(30), Set.of(service), Set.of());
        assertTrue(policy.evaluate(snapshot, service).eligible());
    }

    @Test void returnsAllReasonsNeededForDispatchTelemetry() {
        var snapshot = new ProviderEligibilitySnapshot(PublicId.generate(), ProviderVerificationStatus.SUBMITTED,
                false, false, false, now.minusSeconds(500), Set.of(), Set.of(PublicId.generate()));
        var result = policy.evaluate(snapshot, PublicId.generate());
        assertFalse(result.eligible());
        assertEquals(6, result.reasonCodes().size());
    }
}
