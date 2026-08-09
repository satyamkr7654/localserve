package com.localserve.identityapi;

import com.localserve.config.LocalServeProperties;
import com.localserve.shared.error.DomainException;
import com.localserve.shared.identity.PublicId;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;

@Service
public class CookieSecurityService {
    private static final String LOCAL_REFRESH = "localserve_refresh";
    private static final String HOST_CSRF = "__Host-localserve_csrf";
    private static final String LOCAL_CSRF = "localserve_csrf";
    private static final String HOST_OAUTH_RESULT = "__Host-localserve_oauth_result";
    private static final String LOCAL_OAUTH_RESULT = "localserve_oauth_result";
    private static final String HOST_OAUTH_STATE = "__Host-localserve_oauth_state";
    private static final String LOCAL_OAUTH_STATE = "localserve_oauth_state";
    private final LocalServeProperties properties;
    private final SecureRandom random = new SecureRandom();

    public CookieSecurityService(LocalServeProperties properties) { this.properties = properties; }

    public void writeSession(HttpServletResponse response, PublicId sessionId, String refreshToken,
                             Instant refreshExpiresAt) {
        Duration maxAge = Duration.between(Instant.now(), refreshExpiresAt);
        add(response, ResponseCookie.from(refreshName(), sessionId + "." + refreshToken)
                .httpOnly(true).secure(properties.security().secureCookies()).sameSite("Lax")
                .path("/").maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge).build());
        String csrf = randomToken();
        add(response, ResponseCookie.from(csrfName(), csrf)
                .httpOnly(false).secure(properties.security().secureCookies()).sameSite("Lax")
                .path("/").maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge).build());
        response.setHeader("X-CSRF-Token", csrf);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    public void clearSession(HttpServletResponse response) {
        add(response, expired(refreshName(), true));
        add(response, expired(csrfName(), false));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    public void writeOAuthResult(HttpServletResponse response, String resultToken) {
        add(response, ResponseCookie.from(oauthResultName(), resultToken)
                .httpOnly(true).secure(properties.security().secureCookies()).sameSite("Lax")
                .path("/").maxAge(Duration.ofMinutes(2)).build());
    }

    public void writeOAuthState(HttpServletResponse response, String state) {
        add(response, ResponseCookie.from(oauthStateName(), state)
                .httpOnly(true).secure(properties.security().secureCookies()).sameSite("Lax")
                .path("/").maxAge(Duration.ofMinutes(5)).build());
    }

    public void requireOAuthState(HttpServletRequest request, HttpServletResponse response, String presentedState) {
        String stored = cookie(request, oauthStateName());
        add(response, ResponseCookie.from(oauthStateName(), "").httpOnly(true)
                .secure(properties.security().secureCookies()).sameSite("Lax")
                .path("/").maxAge(Duration.ZERO).build());
        if (stored == null || presentedState == null || !MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8), presentedState.getBytes(StandardCharsets.UTF_8))) {
            throw new DomainException("AUTH.OAUTH_STATE_INVALID", "OAuth state validation failed");
        }
    }

    public String consumeOAuthResult(HttpServletRequest request, HttpServletResponse response) {
        String value = cookie(request, oauthResultName());
        add(response, ResponseCookie.from(oauthResultName(), "").httpOnly(true)
                .secure(properties.security().secureCookies()).sameSite("Lax")
                .path("/").maxAge(Duration.ZERO).build());
        if (value == null) throw new DomainException("AUTH.OAUTH_RESULT_INVALID", "OAuth result is invalid or expired");
        return value;
    }

    public PresentedRefresh requireRefresh(HttpServletRequest request) {
        requireAllowedOrigin(request, true);
        String csrfCookie = cookie(request, csrfName());
        String csrfHeader = request.getHeader("X-CSRF-Token");
        if (csrfCookie == null || csrfHeader == null || !MessageDigest.isEqual(
                csrfCookie.getBytes(StandardCharsets.UTF_8), csrfHeader.getBytes(StandardCharsets.UTF_8))) {
            throw new DomainException("AUTH.CSRF_INVALID", "CSRF validation failed");
        }
        String value = cookie(request, refreshName());
        if (value == null) throw new DomainException("AUTH.INVALID_CREDENTIALS", "Refresh credential is missing");
        int separator = value.indexOf('.');
        if (separator < 1 || separator == value.length() - 1) {
            throw new DomainException("AUTH.INVALID_CREDENTIALS", "Refresh credential is invalid");
        }
        try {
            return new PresentedRefresh(PublicId.parse(value.substring(0, separator)), value.substring(separator + 1));
        } catch (IllegalArgumentException exception) {
            throw new DomainException("AUTH.INVALID_CREDENTIALS", "Refresh credential is invalid");
        }
    }

    public void requireAllowedOrigin(HttpServletRequest request, boolean mandatory) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || origin.isBlank()) {
            if (mandatory) throw new DomainException("AUTH.ORIGIN_INVALID", "A trusted request origin is required");
            return;
        }
        Set<String> allowed = Set.copyOf(properties.security().allowedOrigins());
        if (!allowed.contains(origin)) throw new DomainException("AUTH.ORIGIN_INVALID", "Request origin is not allowed");
    }

    private String refreshName() { return properties.security().secureCookies() ? properties.security().refreshCookieName() : LOCAL_REFRESH; }
    private String csrfName() { return properties.security().secureCookies() ? HOST_CSRF : LOCAL_CSRF; }
    private String oauthResultName() { return properties.security().secureCookies() ? HOST_OAUTH_RESULT : LOCAL_OAUTH_RESULT; }
    private String oauthStateName() { return properties.security().secureCookies() ? HOST_OAUTH_STATE : LOCAL_OAUTH_STATE; }
    private ResponseCookie expired(String name, boolean httpOnly) {
        return ResponseCookie.from(name, "").httpOnly(httpOnly).secure(properties.security().secureCookies())
                .sameSite("Lax").path("/").maxAge(Duration.ZERO).build();
    }
    private static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
    private static void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
    private String randomToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record PresentedRefresh(PublicId sessionId, String token) { }
}
