package com.localserve.identity.session;

import com.localserve.shared.identity.PublicId;

import java.time.Instant;

public interface RefreshTokenRepository {
    void create(RefreshTokenRecord token);

    /** Atomically rotates an ACTIVE token or revokes its family when a rotated token is reused. */
    RefreshRotationOutcome rotate(String presentedHash, PublicId expectedSessionId,
                                  PublicId replacementTokenId, String replacementHash,
                                  Instant rotatedAt, Instant regularExpiresAt, Instant rememberedExpiresAt);

    void revokeFamily(PublicId sessionId, PublicId familyId, String reasonCode, Instant revokedAt);

    void revokeSession(PublicId sessionId, String reasonCode, Instant revokedAt);
}
