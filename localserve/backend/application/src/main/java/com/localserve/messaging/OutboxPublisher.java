package com.localserve.messaging;

import com.localserve.booking.infrastructure.mongo.OutboxDocument;
import com.localserve.config.LocalServeProperties;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** At-least-once publisher. Consumers must deduplicate by eventId. */
@Component
public class OutboxPublisher {
    private static final Duration CLAIM_TTL = Duration.ofSeconds(30);
    private final MongoTemplate mongo;
    private final KafkaTemplate<String, Object> kafka;
    private final Clock clock;
    private final int batchSize;
    private final String workerId = UUID.randomUUID().toString();

    public OutboxPublisher(MongoTemplate mongo, KafkaTemplate<String, Object> kafka,
                           Clock clock, LocalServeProperties properties) {
        this.mongo = mongo; this.kafka = kafka; this.clock = clock;
        this.batchSize = properties.runtime().outboxBatchSize();
    }

    @Scheduled(fixedDelayString = "${OUTBOX_POLL_DELAY_MS:250}")
    public void publishAvailable() {
        for (int index = 0; index < batchSize; index++) {
            OutboxDocument event = claimOne();
            if (event == null) return;
            try {
                kafka.send(topic(event.eventType), event.aggregateId, envelope(event)).get(10, TimeUnit.SECONDS);
                markPublished(event.id);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                markFailed(event, "PUBLISH_INTERRUPTED");
                return;
            } catch (Exception failure) {
                markFailed(event, "KAFKA_PUBLISH_FAILED");
            }
        }
    }

    private OutboxDocument claimOne() {
        Instant now = clock.instant();
        Criteria availableClaim = new Criteria().orOperator(
                Criteria.where("claimedBy").exists(false), Criteria.where("claimUntil").lt(now));
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("publishedAt").is(null), Criteria.where("nextAttemptAt").lte(now), availableClaim));
        query.with(Sort.by(Sort.Direction.ASC, "occurredAt"));
        Update claim = new Update().set("claimedBy", workerId).set("claimUntil", now.plus(CLAIM_TTL)).inc("attempts", 1);
        return mongo.findAndModify(query, claim, FindAndModifyOptions.options().returnNew(true), OutboxDocument.class);
    }

    private void markPublished(String id) {
        Query owned = Query.query(Criteria.where("_id").is(id).and("claimedBy").is(workerId));
        mongo.updateFirst(owned, new Update().set("publishedAt", clock.instant()).unset("claimedBy").unset("claimUntil"), OutboxDocument.class);
    }

    private void markFailed(OutboxDocument event, String safeErrorCode) {
        long delaySeconds = Math.min(300, 1L << Math.min(event.attempts, 8));
        Query owned = Query.query(Criteria.where("_id").is(event.id).and("claimedBy").is(workerId));
        mongo.updateFirst(owned, new Update().set("nextAttemptAt", clock.instant().plusSeconds(delaySeconds))
                .set("lastErrorCode", safeErrorCode).unset("claimedBy").unset("claimUntil"), OutboxDocument.class);
    }

    private static String topic(String eventType) {
        if (eventType.startsWith("localserve.booking.")) return "booking.events.v1";
        throw new IllegalArgumentException("No topic mapping for event type " + eventType);
    }

    private static Map<String, Object> envelope(OutboxDocument event) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", event.eventId); envelope.put("eventType", event.eventType);
        envelope.put("occurredAt", event.occurredAt); envelope.put("aggregateType", event.aggregateType);
        envelope.put("aggregateId", event.aggregateId); envelope.put("aggregateVersion", event.aggregateVersion);
        envelope.put("correlationId", event.correlationId); envelope.put("payload", event.payload);
        return Map.copyOf(envelope);
    }
}
