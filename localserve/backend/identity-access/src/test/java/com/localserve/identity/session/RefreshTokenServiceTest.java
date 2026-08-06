package com.localserve.identity.session;

import com.localserve.shared.error.DomainException;
import com.localserve.shared.identity.PublicId;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenServiceTest {
    @Test void rotatesWithinServerOwnedFamilyAndDetectsReuse() {
        var repository = new InMemoryRepository();
        var service = new RefreshTokenService(repository,
                Clock.fixed(Instant.parse("2026-08-06T10:00:00Z"), ZoneOffset.UTC), new SecureRandom(),
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8),
                Duration.ofDays(14), Duration.ofDays(60));
        PublicId sessionId = PublicId.generate();
        IssuedRefreshToken first = service.issue(sessionId, PublicId.generate(), true);
        IssuedRefreshToken second = service.rotate(first.token(), sessionId);
        assertEquals(first.familyId(), second.familyId());
        DomainException reuse = assertThrows(DomainException.class, () -> service.rotate(first.token(), sessionId));
        assertEquals("AUTH.REFRESH_REUSE_DETECTED", reuse.code());
    }

    private static final class InMemoryRepository implements RefreshTokenRepository {
        private final Map<String, RefreshTokenRecord> tokens = new HashMap<>();
        @Override public void create(RefreshTokenRecord token) { tokens.put(token.tokenHash(), token); }

        @Override public RefreshRotationOutcome rotate(String hash, PublicId expectedSessionId, PublicId replacementId,
                                                        String replacementHash, Instant now, Instant regularExpiry,
                                                        Instant rememberedExpiry) {
            RefreshTokenRecord source = tokens.get(hash);
            if (source == null) return RefreshRotationOutcome.failed(RefreshRotationResult.NOT_FOUND);
            if (!source.sessionId().equals(expectedSessionId)) return RefreshRotationOutcome.failed(RefreshRotationResult.SESSION_MISMATCH);
            if (source.status() == RefreshTokenStatus.ROTATED) {
                revokeFamily(source.sessionId(), source.familyId(), "REUSE", now);
                return RefreshRotationOutcome.failed(RefreshRotationResult.REUSED);
            }
            if (source.status() == RefreshTokenStatus.REVOKED) return RefreshRotationOutcome.failed(RefreshRotationResult.REVOKED);
            if (!source.expiresAt().isAfter(now)) return RefreshRotationOutcome.failed(RefreshRotationResult.EXPIRED);
            tokens.put(hash, copyWithStatus(source, RefreshTokenStatus.ROTATED));
            Instant expiry = source.remembered() ? rememberedExpiry : regularExpiry;
            var replacement = new RefreshTokenRecord(replacementId, source.sessionId(), source.principalId(),
                    source.familyId(), source.id(), replacementHash, now, expiry, RefreshTokenStatus.ACTIVE, source.remembered());
            tokens.put(replacementHash, replacement);
            return new RefreshRotationOutcome(RefreshRotationResult.ROTATED, source.principalId(), source.familyId(), expiry);
        }

        @Override public void revokeFamily(PublicId sessionId, PublicId familyId, String reasonCode, Instant revokedAt) {
            tokens.replaceAll((hash, token) -> token.familyId().equals(familyId) ? copyWithStatus(token, RefreshTokenStatus.REVOKED) : token);
        }
        @Override public void revokeSession(PublicId sessionId, String reasonCode, Instant revokedAt) {
            tokens.replaceAll((hash, token) -> token.sessionId().equals(sessionId) ? copyWithStatus(token, RefreshTokenStatus.REVOKED) : token);
        }
        private static RefreshTokenRecord copyWithStatus(RefreshTokenRecord source, RefreshTokenStatus status) {
            return new RefreshTokenRecord(source.id(), source.sessionId(), source.principalId(), source.familyId(),
                    source.parentTokenId(), source.tokenHash(), source.issuedAt(), source.expiresAt(), status, source.remembered());
        }
    }
}
