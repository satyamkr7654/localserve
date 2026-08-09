package com.localserve.identityapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localserve.shared.error.DomainException;
import com.localserve.shared.identity.PublicId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class GoogleOAuthService {
    private static final String STATE_PREFIX = "localserve:google-oauth-state:";
    private static final String RESULT_PREFIX = "localserve:google-oauth-result:";
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final IdentityPersistence persistence;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final RestClient http;
    private final NimbusJwtDecoder googleTokens;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String customerResultUrl;
    private final String providerResultUrl;

    public GoogleOAuthService(StringRedisTemplate redis, ObjectMapper json, IdentityPersistence persistence,
                              Clock clock, RestClient.Builder builder,
                              @Value("${GOOGLE_OAUTH_CLIENT_ID:}") String clientId,
                              @Value("${GOOGLE_OAUTH_CLIENT_SECRET:}") String clientSecret,
                              @Value("${GOOGLE_OAUTH_REDIRECT_URI:http://localhost:8080/api/v1/auth/oauth/google/callback}") String redirectUri,
                              @Value("${GOOGLE_CUSTOMER_RESULT_URL:http://localhost:3000/oauth/google/result}") String customerResultUrl,
                              @Value("${GOOGLE_PROVIDER_RESULT_URL:http://localhost:3001/oauth/google/result}") String providerResultUrl) {
        this.redis = redis;
        this.json = json;
        this.persistence = persistence;
        this.clock = clock;
        this.http = builder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.customerResultUrl = customerResultUrl;
        this.providerResultUrl = providerResultUrl;
        this.googleTokens = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build();
        this.googleTokens.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                new JwtClaimValidator<String>("iss", issuer -> Set.of("accounts.google.com", "https://accounts.google.com").contains(issuer)),
                new JwtClaimValidator<java.util.List<String>>("aud", audience -> audience != null && audience.contains(clientId))));
    }

    public AuthorizationRequest begin(String role) {
        requireConfigured();
        String normalizedRole = role == null ? "CUSTOMER" : role.toUpperCase(Locale.ROOT);
        if (!Set.of("CUSTOMER", "PROVIDER").contains(normalizedRole)) throw invalid("AUTH.OAUTH_ROLE_INVALID");
        String state = randomToken(32);
        String nonce = randomToken(24);
        String verifier = randomToken(48);
        String challenge = sha256(verifier);
        State payload = new State(nonce, verifier, normalizedRole,
                normalizedRole.equals("PROVIDER") ? providerResultUrl : customerResultUrl);
        write(STATE_PREFIX + state, payload, Duration.ofMinutes(5));
        String uri = UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", clientId).queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code").queryParam("scope", "openid email profile")
                .queryParam("state", state).queryParam("nonce", nonce)
                .queryParam("code_challenge", challenge).queryParam("code_challenge_method", "S256")
                .build().encode().toUriString();
        return new AuthorizationRequest(uri, state, clock.instant().plus(Duration.ofMinutes(5)));
    }

    public CallbackResult complete(String code, String state) {
        requireConfigured();
        State stored = readAndDelete(STATE_PREFIX + state, State.class);
        if (stored == null) throw invalid("AUTH.OAUTH_STATE_INVALID");
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");
        form.add("code_verifier", stored.verifier());
        @SuppressWarnings("unchecked")
        Map<String, Object> response = http.post().uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
        if (response == null || !(response.get("id_token") instanceof String idToken)) throw invalid("AUTH.OAUTH_EXCHANGE_FAILED");
        Jwt identity;
        try {
            identity = googleTokens.decode(idToken);
        } catch (JwtException error) {
            throw invalid("AUTH.OAUTH_IDENTITY_INVALID");
        }
        if (!stored.nonce().equals(identity.getClaimAsString("nonce"))
                || !Boolean.TRUE.equals(identity.getClaim("email_verified"))) {
            throw invalid("AUTH.OAUTH_IDENTITY_INVALID");
        }
        String subject = identity.getSubject();
        String email = identity.getClaimAsString("email").toLowerCase(Locale.ROOT);
        IdentityPersistence.Account account = persistence.findByGoogleSubject(subject).orElse(null);
        String status = "READY";
        if (account == null) {
            IdentityPersistence.Account existingEmail = persistence.findByEmail(email).orElse(null);
            if (existingEmail != null) {
                account = existingEmail;
                status = "LINK_REQUIRED";
            } else {
                account = createAccount(identity, stored.role(), subject, email);
            }
        }
        String result = randomToken(32);
        write(RESULT_PREFIX + result, new Result(status, account.id), Duration.ofSeconds(90));
        return new CallbackResult(result, stored.resultUrl());
    }

    public ExchangeResult exchange(String resultToken) {
        Result result = readAndDelete(RESULT_PREFIX + resultToken, Result.class);
        if (result == null) throw invalid("AUTH.OAUTH_RESULT_INVALID");
        return new ExchangeResult(result.status(), PublicId.parse(result.accountId()));
    }

    private IdentityPersistence.Account createAccount(Jwt identity, String role, String subject, String email) {
        Instant now = clock.instant();
        IdentityPersistence.Account account = new IdentityPersistence.Account();
        account.id = PublicId.generate().toString();
        String name = identity.getClaimAsString("name");
        account.displayName = name == null || name.isBlank() ? email.substring(0, email.indexOf('@')) : name.substring(0, Math.min(80, name.length()));
        account.normalizedEmail = email;
        account.googleSubject = subject;
        account.emailVerified = true;
        account.roles = role.equals("PROVIDER") ? Set.of("CUSTOMER", "PROVIDER") : Set.of("CUSTOMER");
        account.permissions = Set.of();
        account.activeRole = role;
        account.providerOnboardingStatus = role.equals("PROVIDER") ? "DRAFT" : null;
        account.status = "ACTIVE";
        account.locale = "en";
        account.timeZone = "UTC";
        account.acceptedTermsVersion = "oauth-pending";
        account.createdAt = now;
        account.updatedAt = now;
        try {
            return persistence.createAccount(account);
        } catch (DuplicateKeyException race) {
            throw new DomainException("AUTH.OAUTH_LINK_REQUIRED",
                    "Sign in with the existing account before linking Google");
        }
    }

    private <T> void write(String key, T value, Duration ttl) {
        try {
            redis.opsForValue().set(key, json.writeValueAsString(value), ttl);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to persist OAuth transaction", error);
        }
    }

    private <T> T readAndDelete(String key, Class<T> type) {
        String value = redis.opsForValue().getAndDelete(key);
        if (value == null) return null;
        try { return json.readValue(value, type); }
        catch (Exception error) { throw invalid("AUTH.OAUTH_TRANSACTION_INVALID"); }
    }

    private void requireConfigured() {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new DomainException("AUTH.OAUTH_NOT_CONFIGURED", "Google sign-in is not configured");
        }
    }

    private String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String sha256(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception error) { throw new IllegalStateException("SHA-256 is unavailable", error); }
    }

    private static DomainException invalid(String code) {
        return new DomainException(code, "Google sign-in could not be completed");
    }

    record State(String nonce, String verifier, String role, String resultUrl) { }
    record Result(String status, String accountId) { }
    public record AuthorizationRequest(String authorizationUrl, String state, Instant expiresAt) { }
    public record CallbackResult(String resultToken, String redirectUrl) { }
    public record ExchangeResult(String status, PublicId accountId) { }
}
