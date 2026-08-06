package com.localserve.identity.session;

public enum RefreshRotationResult {
    ROTATED,
    REUSED,
    REVOKED,
    EXPIRED,
    NOT_FOUND,
    SESSION_MISMATCH
}
