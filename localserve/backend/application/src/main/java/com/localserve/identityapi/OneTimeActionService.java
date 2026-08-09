package com.localserve.identityapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localserve.shared.error.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class OneTimeActionService {
    private static final String PREFIX = "localserve:auth-action:";
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final SecureRandom random = new SecureRandom();
    private final byte[] pepper;

    public OneTimeActionService(StringRedisTemplate redis, ObjectMapper json,
                                @Value("${AUTH_ACTION_TOKEN_PEPPER:${REFRESH_TOKEN_PEPPER}}") String pepper) {
        this.redis = redis;
        this.json = json;
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
        if (this.pepper.length < 32) throw new IllegalArgumentException("AUTH_ACTION_TOKEN_PEPPER must contain at least 32 bytes");
    }

    public String issue(String purpose, String principalId, String subject, Duration ttl) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            redis.opsForValue().set(PREFIX + hash(token),
                    json.writeValueAsString(new Action(purpose, principalId, subject)), ttl);
            return token;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist one-time authentication action", exception);
        }
    }

    public Action consume(String token, String expectedPurpose) {
        requireShape(token);
        String payload = redis.opsForValue().getAndDelete(PREFIX + hash(token));
        if (payload == null) throw invalid();
        try {
            Action action = json.readValue(payload, Action.class);
            if (!expectedPurpose.equals(action.purpose())) throw invalid();
            return action;
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private String hash(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(token.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    private static void requireShape(String token) {
        if (token == null || token.length() < 40 || token.length() > 128 || !token.matches("[A-Za-z0-9_-]+")) throw invalid();
    }

    private static DomainException invalid() {
        return new DomainException("AUTH.ACTION_TOKEN_INVALID", "The authentication action is invalid or expired");
    }

    public record Action(String purpose, String principalId, String subject) { }
}
