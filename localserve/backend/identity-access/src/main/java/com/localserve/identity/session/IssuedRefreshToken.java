package com.localserve.identity.session;

import com.localserve.shared.identity.PublicId;

import java.time.Instant;

public record IssuedRefreshToken(String token, PublicId tokenId, PublicId familyId, Instant expiresAt) {
}
