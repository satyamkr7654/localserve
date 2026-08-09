/* Run with: mongosh "$MONGODB_URI" infrastructure/mongodb/migrations/002_identity_authentication_indexes.js */
const database = db.getSiblingDB("localserve");

database.identity_accounts.createIndex(
  {normalizedEmail: 1},
  {name: "identity_email_unique", unique: true, partialFilterExpression: {normalizedEmail: {$type: "string"}}}
);
database.identity_accounts.createIndex(
  {normalizedPhone: 1},
  {name: "identity_phone_unique", unique: true, partialFilterExpression: {normalizedPhone: {$type: "string"}}}
);
database.identity_accounts.createIndex(
  {googleSubject: 1},
  {name: "identity_google_subject_unique", unique: true, partialFilterExpression: {googleSubject: {$type: "string"}}}
);
database.identity_sessions.createIndex(
  {principalId: 1, revokedAt: 1, lastSeenAt: -1},
  {name: "identity_active_sessions"}
);
database.identity_sessions.createIndex(
  {principalId: 1, deviceId: 1, lastSeenAt: -1},
  {name: "identity_device_sessions"}
);
database.authentication_activity.createIndex(
  {principalId: 1, occurredAt: -1},
  {name: "authentication_activity_principal"}
);
database.authentication_activity.createIndex(
  {correlationId: 1},
  {name: "authentication_activity_correlation", sparse: true}
);
