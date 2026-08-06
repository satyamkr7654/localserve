package com.localserve.identity.otp;

import com.localserve.shared.identity.PublicId;

import java.time.Instant;
import java.util.Objects;

public record OtpChallenge(
        PublicId id,
        String subjectHash,
        OtpPurpose purpose,
        String codeHash,
        Instant issuedAt,
        Instant expiresAt,
        int maxAttempts,
        long issuanceVersion) {
    public OtpChallenge {
        Objects.requireNonNull(id, "id");
        if (subjectHash == null || subjectHash.length() < 32 || subjectHash.length() > 128) {
            throw new IllegalArgumentException("subjectHash has an invalid length");
        }
        Objects.requireNonNull(purpose, "purpose");
        if (codeHash == null || codeHash.length() != 64) {
            throw new IllegalArgumentException("codeHash must be a SHA-256 hex digest");
        }
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt) || maxAttempts < 1 || maxAttempts > 10 || issuanceVersion < 0) {
            throw new IllegalArgumentException("OTP policy is invalid");
        }
    }
}
