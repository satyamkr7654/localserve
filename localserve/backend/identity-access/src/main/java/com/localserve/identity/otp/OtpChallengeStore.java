package com.localserve.identity.otp;

import com.localserve.shared.identity.PublicId;

import java.time.Instant;

public interface OtpChallengeStore {
    void create(OtpChallenge challenge);

    /** Must atomically validate, increment attempts/lock, and consume on success. */
    OtpAttemptResult attempt(PublicId challengeId, OtpPurpose purpose, String subjectHash,
                             String candidateHash, Instant attemptedAt);
}
