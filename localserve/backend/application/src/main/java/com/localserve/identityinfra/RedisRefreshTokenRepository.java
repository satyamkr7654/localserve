package com.localserve.identityinfra;

import com.localserve.identity.session.*;
import com.localserve.shared.identity.PublicId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Repository
public class RedisRefreshTokenRepository implements RefreshTokenRepository {
    private static final String TOKEN = "localserve:rt:";
    private static final String FAMILY = "localserve:rt-family:";
    private static final String SESSION = "localserve:rt-session:";
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> ROTATE = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return {'NOT_FOUND','',''} end
            local sessionId = redis.call('HGET', KEYS[1], 'sessionId')
            if sessionId ~= ARGV[1] then return {'SESSION_MISMATCH','',''} end
            local familyId = redis.call('HGET', KEYS[1], 'familyId')
            local principalId = redis.call('HGET', KEYS[1], 'principalId')
            local status = redis.call('HGET', KEYS[1], 'status')
            if status == 'ROTATED' then
              local members = redis.call('SMEMBERS', KEYS[2])
              for _,key in ipairs(members) do
                if redis.call('EXISTS', key) == 1 then redis.call('HSET', key, 'status', 'REVOKED') end
              end
              return {'REUSED','',''}
            end
            if status == 'REVOKED' then return {'REVOKED','',''} end
            if tonumber(redis.call('HGET', KEYS[1], 'expiresAt')) <= tonumber(ARGV[4]) then return {'EXPIRED','',''} end
            redis.call('HSET', KEYS[1], 'status', 'ROTATED', 'rotatedAt', ARGV[4], 'replacedById', ARGV[2])
            local remembered = redis.call('HGET', KEYS[1], 'remembered')
            local replacementExpiry = ARGV[5]
            if remembered == 'true' then replacementExpiry = ARGV[6] end
            redis.call('HSET', KEYS[4], 'tokenId', ARGV[2], 'sessionId', sessionId, 'principalId', principalId,
              'familyId', familyId, 'parentTokenId', redis.call('HGET', KEYS[1], 'tokenId'), 'status', 'ACTIVE',
              'issuedAt', ARGV[4], 'expiresAt', replacementExpiry, 'remembered', remembered)
            redis.call('PEXPIREAT', KEYS[4], replacementExpiry)
            redis.call('SADD', KEYS[2], KEYS[4]); redis.call('SADD', KEYS[3], KEYS[4])
            redis.call('PEXPIREAT', KEYS[2], replacementExpiry); redis.call('PEXPIREAT', KEYS[3], replacementExpiry)
            return {'ROTATED',principalId,familyId,replacementExpiry}
            """, List.class);
    private static final DefaultRedisScript<Long> REVOKE_SET = new DefaultRedisScript<>("""
            local members = redis.call('SMEMBERS', KEYS[1])
            for _,key in ipairs(members) do
              if redis.call('EXISTS', key) == 1 then redis.call('HSET', key, 'status', 'REVOKED', 'revokedAt', ARGV[1], 'reasonCode', ARGV[2]) end
            end
            return #members
            """, Long.class);

    private final StringRedisTemplate redis;
    public RedisRefreshTokenRepository(StringRedisTemplate redis) { this.redis = redis; }

    @Override public void create(RefreshTokenRecord token) {
        String tokenKey = tokenKey(token.sessionId(), token.tokenHash());
        redis.opsForHash().putAll(tokenKey, Map.of(
                "tokenId", token.id().toString(), "sessionId", token.sessionId().toString(),
                "principalId", token.principalId().toString(), "familyId", token.familyId().toString(),
                "parentTokenId", token.parentTokenId() == null ? "" : token.parentTokenId().toString(),
                "status", token.status().name(), "issuedAt", Long.toString(token.issuedAt().toEpochMilli()),
                "expiresAt", Long.toString(token.expiresAt().toEpochMilli()),
                "remembered", Boolean.toString(token.remembered())));
        redis.expireAt(tokenKey, token.expiresAt());
        addMembership(familyKey(token.sessionId(), token.familyId()), tokenKey, token.expiresAt());
        addMembership(sessionKey(token.sessionId()), tokenKey, token.expiresAt());
    }

    @SuppressWarnings("unchecked")
    @Override public RefreshRotationOutcome rotate(String presentedHash, PublicId expectedSessionId,
                                                    PublicId replacementTokenId, String replacementHash,
                                                    Instant rotatedAt, Instant regularExpiresAt,
                                                    Instant rememberedExpiresAt) {
        String source = tokenKey(expectedSessionId, presentedHash);
        String familyId = (String) redis.opsForHash().get(source, "familyId");
        List<String> result = (List<String>) redis.execute(ROTATE,
                List.of(source, familyKey(expectedSessionId, familyId == null ? "missing" : familyId),
                        sessionKey(expectedSessionId), tokenKey(expectedSessionId, replacementHash)),
                expectedSessionId.toString(), replacementTokenId.toString(), replacementHash,
                Long.toString(rotatedAt.toEpochMilli()), Long.toString(regularExpiresAt.toEpochMilli()),
                Long.toString(rememberedExpiresAt.toEpochMilli()));
        if (result == null || result.isEmpty()) return RefreshRotationOutcome.failed(RefreshRotationResult.NOT_FOUND);
        RefreshRotationResult status = RefreshRotationResult.valueOf(result.get(0));
        return status == RefreshRotationResult.ROTATED
                ? new RefreshRotationOutcome(status, PublicId.parse(result.get(1)), PublicId.parse(result.get(2)),
                    Instant.ofEpochMilli(Long.parseLong(result.get(3))))
                : RefreshRotationOutcome.failed(status);
    }

    @Override public void revokeFamily(PublicId sessionId, PublicId familyId, String reasonCode, Instant revokedAt) {
        redis.execute(REVOKE_SET, List.of(familyKey(sessionId, familyId)), Long.toString(revokedAt.toEpochMilli()), reasonCode);
    }
    @Override public void revokeSession(PublicId sessionId, String reasonCode, Instant revokedAt) {
        redis.execute(REVOKE_SET, List.of(sessionKey(sessionId)), Long.toString(revokedAt.toEpochMilli()), reasonCode);
    }
    private void addMembership(String setKey, String tokenKey, Instant expiry) {
        redis.opsForSet().add(setKey, tokenKey); redis.expireAt(setKey, expiry);
    }
    private static String slot(PublicId sessionId) { return "{" + sessionId + "}"; }
    private static String tokenKey(PublicId sessionId, String hash) { return TOKEN + slot(sessionId) + ":" + hash; }
    private static String familyKey(PublicId sessionId, PublicId familyId) { return familyKey(sessionId, familyId.toString()); }
    private static String familyKey(PublicId sessionId, String familyId) { return FAMILY + slot(sessionId) + ":" + familyId; }
    private static String sessionKey(PublicId sessionId) { return SESSION + slot(sessionId); }
}
