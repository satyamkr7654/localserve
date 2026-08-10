/* Run with: mongosh "$MONGODB_URI" infrastructure/mongodb/migrations/003_phase8_booking_dispatch_indexes.js */
const database = db.getSiblingDB("localserve");

database.phase8_provider_profiles.createIndex(
  {onboardingStatus: 1, online: 1, serviceZoneId: 1, serviceCodes: 1},
  {name: "phase8_dispatch_eligibility"}
);
database.phase8_booking_quotes.createIndex(
  {expiresAt: 1},
  {name: "phase8_quote_expiry_ttl", expireAfterSeconds: 0}
);
database.phase8_booking_quotes.createIndex(
  {customerId: 1, createdAt: -1},
  {name: "phase8_customer_quotes"}
);
database.phase8_booking_views.createIndex(
  {customerId: 1, createdAt: -1},
  {name: "phase8_customer_bookings"}
);
database.phase8_booking_views.createIndex(
  {assignedProviderId: 1, createdAt: -1},
  {name: "phase8_assigned_provider_bookings"}
);
database.phase8_provider_offers.createIndex(
  {providerId: 1, status: 1, createdAt: -1},
  {name: "phase8_provider_offer_queue"}
);
database.phase8_provider_offers.createIndex(
  {bookingId: 1, status: 1, createdAt: 1},
  {name: "phase8_booking_offers"}
);
database.phase8_local_test_holds.createIndex(
  {bookingId: 1},
  {name: "phase8_one_local_hold_per_booking", unique: true}
);
