package com.localserve.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localserve.web.ApiProblem;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RedisRateLimitFilter extends OncePerRequestFilter {
    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final byte[] pepper;

    public RedisRateLimitFilter(StringRedisTemplate redis, ObjectMapper json,
                                @Value("${RATE_LIMIT_PEPPER}") String pepper) {
        this.redis = redis; this.json = json; this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
        if (this.pepper.length < 32) throw new IllegalArgumentException("RATE_LIMIT_PEPPER must contain at least 32 bytes");
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || "OPTIONS".equals(request.getMethod());
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        Policy policy = policy(request.getRequestURI());
        long bucket = System.currentTimeMillis() / policy.windowMillis;
        String key = "localserve:rate:" + policy.name + ":" + hmac(request.getRemoteAddr()) + ":" + bucket;
        try {
            Long count = redis.execute(INCREMENT, List.of(key), Long.toString(policy.windowMillis));
            response.setHeader("X-RateLimit-Limit", Integer.toString(policy.limit));
            response.setHeader("X-RateLimit-Remaining", Integer.toString(Math.max(0, policy.limit - (count == null ? policy.limit : count.intValue()))));
            if (count != null && count > policy.limit) {
                response.setHeader("Retry-After", Long.toString(Math.max(1, policy.windowMillis / 1000)));
                writeProblem(response, 429, "RATE_LIMIT.EXCEEDED", "Too many requests", request.getRequestURI());
                return;
            }
        } catch (DataAccessException unavailable) {
            if (policy.failClosed) {
                writeProblem(response, 503, "RATE_LIMIT.UNAVAILABLE", "Authentication protection is temporarily unavailable", request.getRequestURI());
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void writeProblem(HttpServletResponse response, int status, String code, String detail, String instance) throws IOException {
        response.setStatus(status); response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), new ApiProblem("https://api.localserve.example/problems/" + code.toLowerCase().replace('.', '-'),
                status == 429 ? "Too Many Requests" : "Service Unavailable", status, code, detail, instance,
                MDC.get("correlationId"), Instant.now(), Map.of()));
    }

    private static Policy policy(String path) {
        if (path.contains("otp") || path.contains("/auth/password")) return new Policy("sensitive-auth", 10, 60_000, true);
        if (path.contains("/auth/")) return new Policy("auth", 20, 60_000, true);
        return new Policy("api", 300, 60_000, false);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (GeneralSecurityException exception) { throw new IllegalStateException("HmacSHA256 is unavailable", exception); }
    }

    private record Policy(String name, int limit, long windowMillis, boolean failClosed) { }
}
