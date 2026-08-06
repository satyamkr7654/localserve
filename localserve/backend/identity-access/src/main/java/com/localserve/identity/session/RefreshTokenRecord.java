package com.localserve.identity.session;

import com.localserve.shared.identity.PublicId;

import java.time.Instant;
import java.util.Objects;

public record RefreshTokenRecord(
        PublicId id,
        PublicId sessionId,
        PublicId principalId,
        PublicId familyId,
        PublicId parentTokenId,
        String tokenHash,
        Instant issuedAt,
        Instant expiresAt,
        RefreshTokenStatus status,
        boolean remembered) {
    public RefreshTokenRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(principalId, "principalId");
        Objects.requireNonNull(familyId, "familyId");
        if (tokenHash == null || tokenHash.length() != 64) {
            throw new IllegalArgumentException("tokenHash must be a SHA-256 hex digest");
        }
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(status, "status");
    }
}
