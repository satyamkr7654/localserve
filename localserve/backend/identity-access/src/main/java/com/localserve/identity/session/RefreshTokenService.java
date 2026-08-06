package com.localserve.identity.session;

import com.localserve.shared.error.DomainException;
import com.localserve.shared.identity.PublicId;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

public final class RefreshTokenService {
    private final RefreshTokenRepository repository;
    private final Clock clock;
    private final SecureRandom random;
    private final byte[] pepper;
    private final Duration regularTtl;
    private final Duration rememberTtl;

    public RefreshTokenService(RefreshTokenRepository repository, Clock clock, SecureRandom random,
                               byte[] pepper, Duration regularTtl, Duration rememberTtl) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
        this.pepper = Objects.requireNonNull(pepper, "pepper").clone();
        this.regularTtl = Objects.requireNonNull(regularTtl, "regularTtl");
        this.rememberTtl = Objects.requireNonNull(rememberTtl, "rememberTtl");
        if (pepper.length < 32 || regularTtl.compareTo(Duration.ofHours(1)) < 0
                || rememberTtl.compareTo(regularTtl) < 0 || rememberTtl.compareTo(Duration.ofDays(90)) > 0) {
            throw new IllegalArgumentException("refresh token security policy is invalid");
        }
    }

    public IssuedRefreshToken issue(PublicId sessionId, PublicId principalId, boolean rememberMe) {
        PublicId familyId = PublicId.generate();
        TokenMaterial material = createMaterial(sessionId, principalId, familyId, null,
                rememberMe ? rememberTtl : regularTtl);
        repository.create(material.record());
        return material.issued();
    }

    public IssuedRefreshToken rotate(String presentedToken, PublicId expectedSessionId) {
        requireTokenShape(presentedToken);
        Instant issuedAt = clock.instant();
        PublicId replacementId = PublicId.generate();
        String plaintext = newPlaintextToken();
        RefreshRotationOutcome outcome = repository.rotate(hash(presentedToken), expectedSessionId,
                replacementId, hash(plaintext), issuedAt, issuedAt.plus(regularTtl), issuedAt.plus(rememberTtl));
        return switch (outcome.result()) {
            case ROTATED -> new IssuedRefreshToken(plaintext, replacementId,
                    Objects.requireNonNull(outcome.familyId(), "repository familyId"),
                    Objects.requireNonNull(outcome.expiresAt(), "repository expiresAt"));
            case REUSED -> throw new DomainException("AUTH.REFRESH_REUSE_DETECTED", "Refresh token reuse was detected");
            case EXPIRED -> throw new DomainException("AUTH.TOKEN_EXPIRED", "Refresh token has expired");
            case REVOKED -> throw new DomainException("AUTH.SESSION_REVOKED", "Session has been revoked");
            case SESSION_MISMATCH -> throw new DomainException("AUTH.SESSION_MISMATCH", "Refresh token session does not match");
            case NOT_FOUND -> throw new DomainException("AUTH.INVALID_CREDENTIALS", "Refresh token is invalid");
        };
    }

    public void revokeSession(PublicId sessionId, String reasonCode) {
        repository.revokeSession(sessionId, reasonCode, clock.instant());
    }

    private TokenMaterial createMaterial(PublicId sessionId, PublicId principalId, PublicId familyId,
                                         PublicId parentTokenId, Duration ttl) {
        String plaintext = newPlaintextToken();
        PublicId tokenId = PublicId.generate();
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(ttl);
        RefreshTokenRecord record = new RefreshTokenRecord(tokenId, sessionId, principalId, familyId,
                parentTokenId, hash(plaintext), issuedAt, expiresAt, RefreshTokenStatus.ACTIVE,
                ttl.equals(rememberTtl));
        return new TokenMaterial(record, new IssuedRefreshToken(plaintext, tokenId, familyId, expiresAt));
    }

    private String newPlaintextToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    private static void requireTokenShape(String token) {
        if (token == null || token.length() < 40 || token.length() > 128 || !token.matches("[A-Za-z0-9_-]+")) {
            throw new DomainException("AUTH.INVALID_CREDENTIALS", "Refresh token is invalid");
        }
    }

    private record TokenMaterial(RefreshTokenRecord record, IssuedRefreshToken issued) {
    }
}
