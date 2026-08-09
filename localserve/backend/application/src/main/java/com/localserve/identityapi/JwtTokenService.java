package com.localserve.identityapi;

import com.localserve.shared.identity.PublicId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class JwtTokenService {
    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration ttl;
    private final String issuer;
    private final String publicAudience;
    private final String adminAudience;

    public JwtTokenService(JwtEncoder encoder, Clock clock,
                           @Value("${localserve.security.access-token-ttl:PT10M}") Duration ttl,
                           @Value("${JWT_ISSUER:https://api.localserve.example}") String issuer,
                           @Value("${JWT_PUBLIC_AUDIENCE:localserve-public}") String publicAudience,
                           @Value("${JWT_ADMIN_AUDIENCE:localserve-admin}") String adminAudience) {
        this.encoder = encoder;
        this.clock = clock;
        this.ttl = ttl;
        this.issuer = issuer;
        this.publicAudience = publicAudience;
        this.adminAudience = adminAudience;
    }

    public IssuedAccessToken issue(IdentityPersistence.Account account, PublicId sessionId,
                                   Instant authenticatedAt, Instant stepUpAt) {
        Instant issuedAt = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(account.id)
                .audience(List.of(account.isAdmin() ? adminAudience : publicAudience))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ttl))
                .id(UUID.randomUUID().toString())
                .claim("sid", sessionId.toString())
                .claim("roles", List.copyOf(account.roles))
                .claim("active_role", account.activeRole)
                .claim("permissions", List.copyOf(account.permissions))
                .claim("auth_time", authenticatedAt.getEpochSecond())
                .claim("account_status", account.status);
        claims.claim("display_name", account.displayName);
        if (stepUpAt != null) claims.claim("step_up_at", stepUpAt.getEpochSecond());
        String token = encoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
        return new IssuedAccessToken(token, ttl.toSeconds(), issuedAt.plus(ttl));
    }

    public record IssuedAccessToken(String token, long expiresInSeconds, Instant expiresAt) { }
}
