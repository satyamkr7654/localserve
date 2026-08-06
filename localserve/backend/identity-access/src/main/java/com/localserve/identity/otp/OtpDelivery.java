package com.localserve.identity.otp;

import com.localserve.shared.identity.PublicId;

import java.time.Instant;

/** Internal delivery-port value. The plaintext code must never cross a public API or log boundary. */
public record OtpDelivery(PublicId challengeId, String plaintextCode, Instant expiresAt, long issuanceVersion) {
}
