/* Run with: mongosh "$MONGODB_URI" infrastructure/mongodb/migrations/001_core_indexes.js */
const database = db.getSiblingDB("localserve");

database.bookings.createIndex({customerId: 1, status: 1, _id: -1}, {name: "customer_status_id"});
database.bookings.createIndex({assignedProviderId: 1, status: 1, _id: -1}, {name: "provider_status_id"});
database.bookings.createIndex({serviceId: 1, status: 1, _id: -1}, {name: "service_status_id"});
database.booking_status_history.createIndex({bookingId: 1, aggregateVersion: 1}, {name: "booking_version_unique", unique: true});
database.outbox_events.createIndex({eventId: 1}, {name: "event_id_unique", unique: true});
database.outbox_events.createIndex({publishedAt: 1, nextAttemptAt: 1, claimUntil: 1, occurredAt: 1}, {name: "outbox_dispatch"});
database.provider_locations.createIndex({location: "2dsphere"}, {name: "provider_location_2dsphere"});
database.provider_locations.createIndex({providerId: 1}, {name: "provider_location_unique", unique: true});
database.provider_locations_history.createIndex({expiresAt: 1}, {name: "location_history_ttl", expireAfterSeconds: 0});
database.otp_audit.createIndex({expiresAt: 1}, {name: "otp_audit_ttl", expireAfterSeconds: 0});
database.webhook_receipts.createIndex({provider: 1, providerEventId: 1}, {name: "gateway_event_unique", unique: true});
database.webhook_receipts.createIndex({receivedAt: 1}, {name: "webhook_received"});
database.reviews.createIndex({bookingId: 1}, {name: "one_review_per_booking", unique: true});
database.messages.createIndex({conversationId: 1, sequence: 1}, {name: "conversation_sequence_unique", unique: true});
database.messages.createIndex({conversationId: 1, clientMessageId: 1}, {name: "conversation_client_message_unique", unique: true});
database.audit_logs.createIndex({actorId: 1, occurredAt: -1}, {name: "actor_occurred"});
database.audit_logs.createIndex({targetType: 1, targetId: 1, occurredAt: -1}, {name: "target_occurred"});
