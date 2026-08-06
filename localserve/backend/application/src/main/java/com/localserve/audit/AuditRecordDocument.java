package com.localserve.audit;

import com.localserve.shared.security.ActorType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document("audit_logs")
@CompoundIndex(name = "actor_occurred", def = "{'actorId':1,'occurredAt':-1}")
public class AuditRecordDocument {
    @Id public String id;
    public ActorType actorType;
    public String actorId;
    public String action;
    public String targetType;
    public String targetId;
    public String correlationId;
    public Instant occurredAt;
    public Map<String, String> safeMetadata;
}
