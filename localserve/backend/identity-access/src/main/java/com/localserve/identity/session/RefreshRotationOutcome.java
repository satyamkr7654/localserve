package com.localserve.identity.session;

import com.localserve.shared.identity.PublicId;

import java.time.Instant;

/**
 * Metadata is populated by the repository from the persisted parent token. It is
 * deliberately never accepted from an API caller.
 */
public record RefreshRotationOutcome(
        RefreshRotationResult result,
        PublicId principalId,
        PublicId familyId,
        Instant expiresAt) {

    public static RefreshRotationOutcome failed(RefreshRotationResult result) {
        return new RefreshRotationOutcome(result, null, null, null);
    }
}
