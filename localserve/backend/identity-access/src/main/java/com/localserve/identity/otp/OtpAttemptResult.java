package com.localserve.identity.otp;

public enum OtpAttemptResult {
    VERIFIED,
    INVALID,
    EXPIRED,
    LOCKED,
    CONSUMED,
    NOT_FOUND,
    PURPOSE_OR_SUBJECT_MISMATCH
}
