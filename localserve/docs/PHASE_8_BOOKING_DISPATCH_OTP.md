# Phase 8 — Booking, dispatch and service OTP workflow

Phase 8 turns the Phase 6 preview surfaces into a real multi-account marketplace workflow while preserving the Phase 1–7 specifications and the existing 18-state booking aggregate.

## Implemented scope

- Provider onboarding submission with business name, service zone, supported services and capacity.
- Admin approval/rejection queue. Only an approved provider can go online.
- Server-priced customer quotes with a 15-minute expiry and a fixed Phase 8 local catalog.
- Mongo-backed booking projections and provider offer queues alongside the authoritative booking aggregate, status history and transactional outbox.
- Dispatch to providers that were approved, online, in the same service zone and configured for the requested service when the booking was created.
- Provider offer acceptance, customer provider selection and a local/test-only held-payment confirmation that advances the existing payment-gated state machine.
- Provider journey and arrival transitions.
- Separate, purpose-bound start and completion OTPs backed by the existing atomic Redis challenge store. Codes are emailed to the customer and can be inspected in local Mailpit.
- Customer satisfaction confirmation after provider completion verification.
- Same-origin authenticated BFF routes and real customer, provider and admin workflow screens.

Real Razorpay/Stripe capture, ledger settlement, refunds, commissions and provider release remain Phase 9. The local/test payment endpoint is rejected outside the `local` and `test` environments.

## Local multi-user workflow

Use three browser profiles so the localhost access cookies do not replace one another:

1. Provider on `http://localhost:3001`: register, sign in, open **Profile**, choose a service and use zone `noida-central`, then submit.
2. Admin on `http://localhost:3002`: sign in with the local admin seed, open **Providers**, refresh, and approve the submitted provider.
3. Provider: refresh **Profile**, turn **Receive job offers** on, and leave the account online.
4. Customer on `http://localhost:3000`: register/sign in, choose **Request service**, use the same service and `noida-central`, get a quote, and create the booking.
5. Provider: open **Jobs**, refresh and accept the matching offer.
6. Customer: open **Bookings**, choose the provider, then confirm the clearly labelled local test payment.
7. Provider: start the journey and mark arrival.
8. Customer: send the start OTP. Open `http://localhost:8025`, read the latest six-digit code, and share it with the provider only after arrival.
9. Provider: enter the start OTP, perform the work, then select **Work finished**.
10. Customer: inspect the work and send the completion OTP from the booking page.
11. Provider: enter the completion OTP.
12. Customer: confirm satisfaction. The booking reaches `COMPLETED`.

A provider must already be approved and online when the customer creates the booking. Going online later does not retroactively add an offer to an existing search.

## Database migration

Run migrations after Compose is healthy:

```bash
docker compose -f infrastructure/compose/docker-compose.yml exec -T mongo \
  mongosh "mongodb://localhost:27017/localserve?replicaSet=rs0" \
  --file /dev/stdin < infrastructure/mongodb/migrations/003_phase8_booking_dispatch_indexes.js
```

On Windows PowerShell, use:

```powershell
Get-Content infrastructure/mongodb/migrations/003_phase8_booking_dispatch_indexes.js -Raw |
  docker compose -f infrastructure/compose/docker-compose.yml exec -T mongo \
  mongosh "mongodb://localhost:27017/localserve?replicaSet=rs0" --file /dev/stdin
```

The migration adds dispatch, quote expiry, booking list, offer queue and one-local-hold-per-booking indexes.

## API surface

- Public: `GET /api/v1/public/services`
- Customer: quote creation, booking creation/list/detail, offers, provider selection, local test payment, start/completion OTP issuance and satisfaction confirmation under `/api/v1/customer/**`
- Provider: onboarding, operational status, offer acceptance, assigned bookings, journey, arrival, OTP verification and completion under `/api/v1/provider/**`
- Admin: provider verification queue and decisions under `/api/v1/admin/verification-requests/**`

Every role endpoint derives the account ID from the verified JWT subject. Client-provided customer/provider identity IDs are not trusted.

## Verification

```bash
./scripts/verify-phase8.sh
```

This runs the Java 21 application/module test suite and the complete Phase 6 frontend lint, typecheck, unit-test and production-build pipeline.
