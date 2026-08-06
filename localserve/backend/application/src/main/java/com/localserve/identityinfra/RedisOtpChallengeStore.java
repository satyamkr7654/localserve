package com.localserve.identityinfra;

import com.localserve.identity.otp.OtpAttemptResult;
import com.localserve.identity.otp.OtpChallenge;
import com.localserve.identity.otp.OtpChallengeStore;
import com.localserve.identity.otp.OtpPurpose;
import com.localserve.shared.identity.PublicId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class RedisOtpChallengeStore implements OtpChallengeStore {
    private static final String PREFIX = "localserve:otp:";
    private static final DefaultRedisScript<String> ATTEMPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 'NOT_FOUND' end
            local status = redis.call('HGET', KEYS[1], 'status')
            if status == 'CONSUMED' then return 'CONSUMED' end
            if status == 'LOCKED' then return 'LOCKED' end
            if tonumber(redis.call('HGET', KEYS[1], 'expiresAt')) <= tonumber(ARGV[4]) then return 'EXPIRED' end
            if redis.call('HGET', KEYS[1], 'purpose') ~= ARGV[1] or redis.call('HGET', KEYS[1], 'subjectHash') ~= ARGV[2] then
              return 'PURPOSE_OR_SUBJECT_MISMATCH'
            end
            if redis.call('HGET', KEYS[1], 'codeHash') == ARGV[3] then
              redis.call('HSET', KEYS[1], 'status', 'CONSUMED', 'consumedAt', ARGV[4])
              return 'VERIFIED'
            end
            local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
            if attempts >= tonumber(redis.call('HGET', KEYS[1], 'maxAttempts')) then redis.call('HSET', KEYS[1], 'status', 'LOCKED'); return 'LOCKED' end
            return 'INVALID'
            """, String.class);

    private final StringRedisTemplate redis;
    public RedisOtpChallengeStore(StringRedisTemplate redis) { this.redis = redis; }

    @Override public void create(OtpChallenge challenge) {
        String key = PREFIX + challenge.id();
        redis.opsForHash().putAll(key, java.util.Map.of(
                "subjectHash", challenge.subjectHash(), "purpose", challenge.purpose().name(),
                "codeHash", challenge.codeHash(), "expiresAt", Long.toString(challenge.expiresAt().toEpochMilli()),
                "maxAttempts", Integer.toString(challenge.maxAttempts()), "attempts", "0",
                "issuanceVersion", Long.toString(challenge.issuanceVersion()), "status", "ACTIVE"));
        redis.expireAt(key, challenge.expiresAt());
    }

    @Override public OtpAttemptResult attempt(PublicId challengeId, OtpPurpose purpose, String subjectHash,
                                               String candidateHash, Instant attemptedAt) {
        String result = redis.execute(ATTEMPT, List.of(PREFIX + challengeId), purpose.name(), subjectHash,
                candidateHash, Long.toString(attemptedAt.toEpochMilli()));
        return OtpAttemptResult.valueOf(result == null ? "NOT_FOUND" : result);
    }
}
