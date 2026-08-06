package com.localserve.identity.otp;

import com.localserve.shared.error.DomainException;
import com.localserve.shared.identity.PublicId;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

public final class OtpService {
    private final OtpChallengeStore store;
    private final Clock clock;
    private final SecureRandom random;
    private final byte[] pepper;
    private final Duration ttl;
    private final int maxAttempts;

    public OtpService(OtpChallengeStore store, Clock clock, SecureRandom random, byte[] pepper,
                      Duration ttl, int maxAttempts) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
        this.pepper = Objects.requireNonNull(pepper, "pepper").clone();
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.maxAttempts = maxAttempts;
        if (pepper.length < 32 || ttl.compareTo(Duration.ofMinutes(1)) < 0
                || ttl.compareTo(Duration.ofMinutes(15)) > 0 || maxAttempts < 3 || maxAttempts > 10) {
            throw new IllegalArgumentException("OTP security policy is invalid");
        }
    }

    public OtpDelivery issue(String subjectHash, OtpPurpose purpose, long issuanceVersion) {
        Objects.requireNonNull(subjectHash, "subjectHash");
        Objects.requireNonNull(purpose, "purpose");
        PublicId challengeId = PublicId.generate();
        String code = "%06d".formatted(random.nextInt(1_000_000));
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(ttl);
        String codeHash = hash(challengeId, purpose, subjectHash, code, issuanceVersion);
        store.create(new OtpChallenge(challengeId, subjectHash, purpose, codeHash,
                issuedAt, expiresAt, maxAttempts, issuanceVersion));
        return new OtpDelivery(challengeId, code, expiresAt, issuanceVersion);
    }

    public void verify(PublicId challengeId, String subjectHash, OtpPurpose purpose,
                       String candidateCode, long issuanceVersion) {
        if (candidateCode == null || !candidateCode.matches("[0-9]{6}")) {
            throw new DomainException("AUTH.OTP_INVALID", "OTP is invalid");
        }
        String candidateHash = hash(challengeId, purpose, subjectHash, candidateCode, issuanceVersion);
        OtpAttemptResult result = store.attempt(challengeId, purpose, subjectHash, candidateHash, clock.instant());
        switch (result) {
            case VERIFIED -> { return; }
            case EXPIRED -> throw new DomainException("AUTH.OTP_EXPIRED", "OTP has expired");
            case LOCKED -> throw new DomainException("AUTH.OTP_ATTEMPTS_EXCEEDED", "OTP attempts have been exceeded");
            case CONSUMED -> throw new DomainException("AUTH.OTP_REPLAYED", "OTP has already been used");
            case PURPOSE_OR_SUBJECT_MISMATCH -> throw new DomainException("AUTH.OTP_PURPOSE_MISMATCH", "OTP is not valid for this purpose");
            case INVALID, NOT_FOUND -> throw new DomainException("AUTH.OTP_INVALID", "OTP is invalid");
        }
    }

    private String hash(PublicId challengeId, OtpPurpose purpose, String subjectHash,
                        String code, long issuanceVersion) {
        String input = challengeId + ":" + purpose + ":" + subjectHash + ":" + issuanceVersion + ":" + code;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }
}
