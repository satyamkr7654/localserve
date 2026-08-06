package com.localserve.audit;

import com.localserve.shared.identity.PublicId;
import com.localserve.shared.security.Actor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuditService {
    private static final Set<String> FORBIDDEN_FRAGMENTS = Set.of(
            "password", "otp", "aadhaar", "pan", "token", "secret", "authorization", "card", "bank");
    private final MongoTemplate mongo;
    private final Clock clock;

    public AuditService(MongoTemplate mongo, Clock clock) { this.mongo = mongo; this.clock = clock; }

    /** Append-only by design: this component exposes no update or delete operation. */
    public void record(Actor actor, String action, String targetType, PublicId targetId,
                       PublicId correlationId, Map<String, String> safeMetadata) {
        Objects.requireNonNull(actor, "actor");
        var document = new AuditRecordDocument();
        document.id = PublicId.generate().toString(); document.actorType = actor.type();
        document.actorId = actor.id() == null ? null : actor.id().toString();
        document.action = code(action); document.targetType = code(targetType);
        document.targetId = targetId == null ? null : targetId.toString();
        document.correlationId = Objects.requireNonNull(correlationId, "correlationId").toString();
        document.occurredAt = clock.instant(); document.safeMetadata = sanitize(safeMetadata);
        mongo.insert(document);
    }

    private static Map<String, String> sanitize(Map<String, String> metadata) {
        if (metadata.size() > 20) throw new IllegalArgumentException("audit metadata limit exceeded");
        return metadata.entrySet().stream().collect(Collectors.toUnmodifiableMap(entry -> {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (FORBIDDEN_FRAGMENTS.stream().anyMatch(key::contains)) throw new IllegalArgumentException("sensitive audit metadata key rejected");
            return code(entry.getKey());
        }, entry -> {
            String value = Objects.requireNonNull(entry.getValue(), "audit metadata value");
            if (value.length() > 200) throw new IllegalArgumentException("audit metadata value too long");
            return value;
        }));
    }

    private static String code(String value) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{1,79}")) throw new IllegalArgumentException("invalid audit code");
        return value;
    }
}
