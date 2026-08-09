# Phase 7 — Authentication and session security

Phase 7 implements the authentication contract frozen in `PHASE_4_API_DESIGN.md` without changing the Phase 1–6 product, architecture, data, API, backend, or frontend specifications.

## Implemented scope

- Customer and provider email/password registration with Argon2id, breached-password screening, verified-contact login, and provider onboarding fixed to `DRAFT`.
- Purpose-bound phone OTP challenges backed by the existing atomic Redis challenge store, including expiry, attempt limits, consumption, and enumeration-safe login challenge creation.
- Single-use Redis-backed email verification and password recovery actions. Password reset revokes every active refresh family.
- Google authorization-code sign-in with state, nonce, PKCE S256, exact server redirect configuration, Google ID-token signature/issuer/audience validation, and a 90-second one-time result exchange. Existing email identities are never silently linked.
- RSA-signed short-lived access JWTs with explicit public/admin audiences, roles, permissions, active role, session ID, authentication time, account status, and optional step-up time.
- Opaque rotating refresh tokens stored only in HttpOnly cookies. Refresh reuse revokes the session and is written to the safe authentication activity feed.
- Separate admin password endpoints, mandatory enrolled TOTP when configured, admin-audience tokens, permission claims, step-up challenges, and no public admin registration path.
- Server-side account status, session revocation, and audience checks on every authenticated API request.
- Origin allowlisting plus double-submit CSRF protection for refresh/logout endpoints. Auth and token responses use `Cache-Control: no-store`.
- Customer, provider, and admin login experiences; customer/provider registration, email verification, password reset, Google completion, and MFA screens; same-origin BFF access-token cookies; and authoritative server-side route guards.

## Runtime topology

MongoDB stores accounts, device sessions, and privacy-minimized authentication activity. Redis stores OTPs, refresh-token rotation state, MFA challenges, OAuth transactions, one-time actions, and rate-limit buckets. Mailpit is included in local Compose at `http://localhost:8025`; SMS OTPs use the local in-memory delivery adapter in the `local` and `test` profiles.

Run `infrastructure/mongodb/migrations/002_identity_authentication_indexes.js` after the Phase 5 migration. It creates partial unique identity indexes and session/activity query indexes.

## Configuration

Generate independent random values of at least 32 bytes for `OTP_HMAC_PEPPER`, `REFRESH_TOKEN_PEPPER`, `AUTH_ACTION_TOKEN_PEPPER`, and `RATE_LIMIT_PEPPER`. Outside `local`/`test`, supply an RSA PKCS#8 private key and X.509 public key through `JWT_PRIVATE_KEY_BASE64` and `JWT_PUBLIC_KEY_BASE64`.

Google requires `GOOGLE_OAUTH_CLIENT_ID`, `GOOGLE_OAUTH_CLIENT_SECRET`, and an exactly registered `GOOGLE_OAUTH_REDIRECT_URI`. Its fixed customer/provider result URLs must be approved application URLs. Deploy the browser apps and auth API behind the same trusted ingress when using the BFF cookie model.

For an optional local-only administrator seed, set all of `LOCAL_ADMIN_EMAIL`, `LOCAL_ADMIN_PASSWORD`, and `LOCAL_ADMIN_TOTP_SECRET`. No seed runs when they are blank, and the seeder is not active outside the `local` Spring profile.

## Verification

```bash
./scripts/verify-phase7.sh
```

The command runs the Java 21 Maven test suite and the full frontend lint, typecheck, unit-test, and production-build pipeline. End-to-end browser execution remains opt-in through `RUN_E2E=true` as in Phase 6.
