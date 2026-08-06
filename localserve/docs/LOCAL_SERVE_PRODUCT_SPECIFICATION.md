# LocalServe Marketplace — Central Product Specification

**Document ID:** LS-SPEC-001  
**Document type:** Authoritative product specification and Phase 1 PRD  
**Version:** 1.0.1  
**Status:** Phase 1 complete — baseline candidate for stakeholder approval  
**Date:** 2026-08-06  
**Primary launch market:** India  
**Working product name:** LocalServe Marketplace  
**Stable code identifier:** `localserve`

---

## 1. Document authority and change control

This file is the source of truth for the product across all 14 delivery phases. Later architecture, schemas, APIs, code, tests, infrastructure, and documentation must conform to it.

Changes to a locked decision require an Architecture Decision Record (ADR) containing the reason, alternatives, migration impact, compatibility plan, approval, and effective version. A later phase must not silently rename roles, statuses, API paths, fields, topics, or events.

### 1.1 Requirement language

- **MUST**: mandatory for the stated release and a release blocker if absent.
- **SHOULD**: expected unless an approved ADR records a reason to defer it.
- **MAY**: optional or experimentation-controlled.
- **MVP**: first public production release, not a prototype.
- **V1 Growth**: committed post-launch capability following the same production standards.
- **Future**: architecture-ready but not part of the first two release gates.

### 1.2 Naming and compatibility rules

| Concern | Locked convention |
|---|---|
| Product/code name | `LocalServe Marketplace` / `localserve` |
| API base path | `/api/v1` |
| API media type | `application/json`; UTF-8 |
| Roles | `CUSTOMER`, `PROVIDER`, `ADMIN` |
| Public identifiers | UUIDv7 string; never expose MongoDB `_id` |
| Money | Integer minor units plus ISO 4217 currency, for example `amountMinor: 129900`, `currency: "INR"` |
| Time | UTC internally; ISO-8601 timestamps; client renders the user's IANA timezone |
| Phone | E.164 format |
| Locale | BCP 47 tag, initially `en-IN` |
| Coordinates | GeoJSON longitude first: `[longitude, latitude]` |
| Soft deletion | `deletedAt`, `deletedBy`, and deletion reason where permitted |
| Optimistic locking | Numeric `version` field on mutable high-contention documents |
| Audit fields | `createdAt`, `createdBy`, `updatedAt`, `updatedBy`, `version` |
| Kafka events | `localserve.<domain>.<entity>.<past-tense-event>.v1` |
| Correlation | `X-Correlation-Id`; generated server-side when absent |
| Idempotency | `Idempotency-Key` on financial and other retry-sensitive commands |
| Pagination | Cursor pagination for high-volume feeds; page pagination only for bounded admin lists |

### 1.3 Canonical domain registers

#### Booking statuses

The booking state machine uses exactly these public statuses:

`CREATED`, `SEARCHING_PROVIDERS`, `PROVIDERS_FOUND`, `PROVIDER_SELECTED`, `PAYMENT_PENDING`, `PAYMENT_COMPLETED`, `PROVIDER_ASSIGNED`, `PROVIDER_ON_THE_WAY`, `PROVIDER_ARRIVED`, `START_OTP_PENDING`, `IN_PROGRESS`, `COMPLETION_PENDING`, `CUSTOMER_CONFIRMATION_PENDING`, `COMPLETED`, `DISPUTED`, `CANCELLED`, `REFUNDED`, `CLOSED`.

Every transition must be authorized by the state machine. Direct status assignment outside the booking domain is forbidden.

#### Payment statuses

`CREATED`, `PENDING`, `AUTHORIZED`, `CAPTURED`, `HELD`, `RELEASE_PENDING`, `RELEASED`, `PARTIALLY_REFUNDED`, `REFUNDED`, `FAILED`, `CANCELLED`, `DISPUTED`, `FROZEN`.

#### Provider offer statuses

`PENDING`, `ACCEPTED_BY_PROVIDER`, `REJECTED_BY_PROVIDER`, `SELECTED_BY_CUSTOMER`, `EXPIRED`, `WITHDRAWN`, `NOT_SELECTED`.

#### Provider verification statuses

`DRAFT`, `SUBMITTED`, `UNDER_REVIEW`, `MORE_INFORMATION_REQUIRED`, `APPROVED`, `REJECTED`, `SUSPENDED`, `EXPIRED`.

#### Dispute statuses

`OPEN`, `EVIDENCE_COLLECTION`, `UNDER_REVIEW`, `CUSTOMER_RESPONSE_REQUIRED`, `PROVIDER_RESPONSE_REQUIRED`, `RESOLVED_REFUND`, `RESOLVED_PARTIAL_REFUND`, `RESOLVED_RELEASE`, `CLOSED`, `APPEALED`.

#### Payout statuses

`REQUESTED`, `UNDER_REVIEW`, `APPROVED`, `PROCESSING`, `PAID`, `FAILED`, `REVERSED`, `REJECTED`, `FROZEN`.

#### Notification delivery statuses

`QUEUED`, `SCHEDULED`, `SENT`, `DELIVERED`, `READ`, `FAILED`, `DEAD_LETTERED`, `CANCELLED`.

#### Role-membership rule

A public identity may hold `CUSTOMER` and, after approval, `PROVIDER` memberships so a provider can also purchase a service. Customer and provider routes, dashboards, permissions, and active UI context remain separate; possessing one role never grants the other role's permissions. `ADMIN` identities use a separate administrative account lifecycle and cannot be created by adding an admin role to a public account.

### 1.4 Technology baseline

| Area | Decision |
|---|---|
| Backend | Java 21 LTS, Spring Boot modular monolith, Maven multi-module build |
| API | Spring Web MVC, Jakarta Validation, Springdoc OpenAPI |
| Security | Spring Security, OAuth 2.0 Client, short-lived JWT access tokens, rotating refresh tokens, Argon2id password hashing |
| Operational data | MongoDB replica set as primary operational database |
| Ephemeral/distributed state | Redis |
| Events | Apache Kafka with schema versioning and outbox/inbox patterns |
| Real time | Spring WebSocket + STOMP; Redis Pub/Sub for fan-out; Kafka for durable domain events |
| Web | Next.js, React, TypeScript, Tailwind CSS, shadcn/ui, TanStack Query, Redux Toolkit where global client state is justified |
| Mobile | React Native with TypeScript; separate customer and provider app experiences with shared packages |
| Files | Private S3-compatible object storage through an abstraction; Cloudinary only for approved public/transformed media |
| Maps | Provider abstraction, initially Google Maps Platform |
| Payments | Gateway abstraction, initially Razorpay for India and Stripe where supported; server-verified webhooks only |
| Deployment | Containers, Nginx/ingress, Kubernetes-ready, AWS- and Terraform-ready |
| Observability | Actuator, OpenTelemetry, Prometheus, Grafana, structured JSON logs, Loki/ELK-compatible shipping |

Minor framework versions are pinned in Phase 5/6 lockfiles after compatibility verification. A minor-version update is not a product-spec change if contracts remain compatible.

### 1.5 Financial and identity compliance guardrails

1. “Escrow” in the product UI and internal ledger means a **platform-held payment and delayed settlement workflow**. LocalServe must not represent itself as a bank, payment system operator, licensed escrow provider, or wallet issuer unless it has the required authorization.
2. Customer funds must flow through an authorized payment partner and an approved marketplace/linked-account product where applicable. Legal counsel and the payment partner must approve the exact fund flow, settlement timing, refund flow, provider onboarding, and customer terms before production.
3. The internal escrow ledger is an accounting sub-ledger. It never replaces the payment partner's settlement records or bank statements.
4. The MVP customer wallet is limited to non-withdrawable promotional credits and gateway-backed refunds unless a compliant PPI/banking partner enables stored value. No peer-to-peer transfer or cash-out is allowed.
5. Raw card credentials, CVV, UPI PINs, or internet-banking credentials are never collected or stored by LocalServe.
6. Aadhaar handling defaults to masked Aadhaar or consented UIDAI-supported offline verification. Unmasked Aadhaar copies or numbers are not stored unless a documented lawful basis, approved process, and access-control policy exist.
7. Aadhaar, PAN, bank, tax, location, chat, payment, and identity-document data must be classified as restricted, masked in normal views, encrypted, and excluded from application logs.
8. Privacy notices, consent, data-principal rights, retention, breach response, processor contracts, grievance handling, and cross-border transfer controls must be reviewed against applicable Indian law before launch.

Official reference baseline:

- [Reserve Bank of India — Payment and Settlement Systems Act FAQ](https://www.rbi.org.in/commonman/english/scripts/FAQs.aspx?Id=420)
- [UIDAI — What is Masked Aadhaar?](https://www.uidai.gov.in/en/283-faqs/aadhaar-online-services/e-aadhaar/1887-what-is-masked-aadhaar.html)
- [UIDAI — Dos and Don'ts for Requesting Entities](https://uidai.gov.in/images/Dos_and_Donots_for_Requesting_Entities.pdf)
- [MeitY — Digital Personal Data Protection Rules, 2025](https://www.meity.gov.in/documents/act-and-policies/digital-personal-data-protection-rules-2025-gDOxUjMtQWa)
- [Razorpay Route — marketplace transfers and delayed settlement](https://razorpay.com/docs/payments/route/)

These references inform risk controls; they are not a substitute for legal, tax, payment-partner, or regulatory advice.

### 1.6 Final-year project delivery profile

This project is an **advanced final-year capstone**, suitable for a team of approximately 2–4 students. It must be impressive for university evaluation and placement interviews while remaining understandable, runnable on a student laptop, demonstrable with sandbox credentials, and defensible during a viva.

The millions-of-users requirements are architecture and load-testing targets, not a claim that a student deployment already serves millions of real users. The implementation must show how the design can evolve to that scale without requiring students to operate expensive production infrastructure.

#### Required working capstone

The submitted project must provide a complete end-to-end working vertical slice containing:

- A Java 21/Spring Boot modular-monolith backend with clean module boundaries.
- MongoDB, Redis, and Kafka running locally through Docker Compose.
- Separate customer, provider, and admin web experiences with responsive interfaces.
- Registration/login, role isolation, provider onboarding and admin approval.
- Service catalog, location-aware provider discovery, availability, provider offers, and customer selection.
- The complete booking state machine, Start OTP, Completion OTP, cancellation, timeline, and invoice flow.
- Sandbox/test-mode payment initiation, verified webhook processing, held-funds double-entry ledger, commission, refund, dispute freeze, provider balance, and payout workflow.
- Booking-scoped WebSocket/STOMP updates, chat, read receipts, presence, and live-location demonstration.
- Notifications through local/test adapters and production-ready provider interfaces.
- Completed-booking reviews, dispute evidence, administrative resolution, audit logs, and essential analytics.
- Unit, integration, security, WebSocket, and end-to-end tests for the critical journey.
- Docker deployment, CI pipeline, API documentation, setup guide, demo data, architecture diagrams, and a classroom demonstration script.

#### Controlled integration modes

External services that require commercial approval, regulated access, or paid credentials must use real integration code plus safe modes:

| Integration | Final-year implementation expectation |
|---|---|
| Razorpay and Stripe | Sandbox/test adapters with signature-verified webhook tests; never fake success from the frontend |
| Google OAuth | Real OAuth client when credentials are supplied; documented local test identity provider/fallback for automated tests |
| Maps | Provider abstraction with Google Maps adapter; deterministic local/test adapter for development without a paid key |
| Email | SMTP adapter with Mailpit locally and a production provider configuration path |
| SMS/OTP | Provider interface plus local test delivery sink; OTP generation, hashing, expiry, attempts, and rate limits remain real backend logic |
| FCM | Firebase adapter enabled by credentials plus a local notification sink for tests |
| Object storage | S3-compatible adapter using MinIO locally and private S3-compatible storage in deployment |
| Face/background/police verification | Explicitly integration-ready ports and manual admin workflow; the UI must never claim a check occurred without a real provider result |
| Provider payout | Sandbox-capable gateway where access exists; otherwise a deterministic server-side payout simulator behind the same audited port for classroom demonstration |

#### Scope-control rules

1. The team builds a reliable modular monolith, not dozens of microservices.
2. Core business methods cannot be placeholders. Optional regulated/paid integrations may use documented adapters and test doubles, but booking, authorization, OTP, ledger, refund, freeze, and audit logic must work fully.
3. Kubernetes, Terraform, multi-region, sharding, and very large load profiles are delivered as validated deployment-ready designs/manifests where appropriate; the classroom demo may run with Docker Compose on one laptop or one cloud VM.
4. The student build uses seeded service categories and realistic demo accounts/data so every critical workflow can be evaluated without manual database editing.
5. Code and documentation must favor clarity over unnecessary abstraction. A student should be able to explain each module, state transition, security control, database decision, and event flow.
6. The final submission must distinguish `IMPLEMENTED_AND_TESTED`, `SANDBOX_INTEGRATED`, `SIMULATED_BEHIND_REAL_PORT`, and `ARCHITECTURE_READY` capabilities. It must never misrepresent a simulated or future capability as a live regulated service.

#### Academic evaluation outcomes

The project must demonstrate the following final-year competencies:

- Requirements engineering and product thinking.
- Java, Spring Boot, Spring Security, MongoDB, Redis, Kafka, REST, and WebSocket development.
- React/Next.js/TypeScript frontend engineering and role-specific UX.
- Database/index design, concurrency control, idempotency, event-driven design, and financial-ledger reasoning.
- Authentication, authorization, secure file handling, webhook security, auditability, and OWASP awareness.
- Automated testing, Docker, CI/CD, observability, cloud deployment, documentation, and presentation skills.

This academic constraint is locked for later phases: production principles remain, but implementation choices must be achievable and explainable for final-year students.

---

# Phase 1 — Product Requirement Document

## 2. Product vision

LocalServe is a trusted, location-aware marketplace that helps a customer discover, compare, book, track, pay, and obtain support from verified local professionals in one continuous experience. It also gives service providers a reliable digital operating system for finding work, managing availability, proving service delivery, receiving transparent earnings, and building a portable reputation.

The product should make booking a skilled local worker feel as predictable as requesting a ride: clear availability, transparent offers, real-time status, verified milestones, protected payment, and accountable support.

### 2.1 Product principles

1. **Trust before growth:** Verification, traceability, payment integrity, privacy, and dispute handling are launch requirements.
2. **Customer choice:** The platform may rank offers, but the customer chooses the provider after seeing comparable information.
3. **Provider fairness:** Providers see material job details before accepting, understand fees before work, and receive an auditable earnings statement.
4. **No invisible state changes:** Important booking and money changes create timeline, ledger, event, and audit records.
5. **Server authority:** Booking, OTP, price, payment, permission, and payout decisions are enforced by the backend.
6. **Progressive disclosure:** Interfaces remain simple while advanced evidence, tax, support, and administrative controls are available when needed.
7. **Graceful degradation:** Core workflows remain understandable during map, notification, chat, or payment-provider degradation.
8. **Inclusive by design:** Mobile-first, accessible, low-bandwidth-aware, and ready for multiple Indian languages.

## 3. Problem statement

### 3.1 Customer problems

- Finding a genuinely available and competent local professional is slow and unreliable.
- Prices, arrival time, identity, skills, and reviews are difficult to compare.
- Informal bookings provide weak evidence when a provider does not arrive, work is incomplete, or a payment dispute occurs.
- Contact details and live location are often shared too early or without privacy controls.
- Customers use separate channels for discovery, chat, payment, navigation, and support.
- Emergency needs create pressure to accept the first available person without adequate trust signals.

### 3.2 Provider problems

- Skilled workers depend heavily on word of mouth and experience uneven demand.
- Existing marketplaces may provide limited control over availability, pricing, service radius, and job selection.
- Providers struggle to prove work quality, track earnings, reconcile fees, and build a verified reputation.
- Cancellations, unreachable customers, payment delays, and unfair complaints reduce effective income.
- Verification and document management are fragmented.

### 3.3 Platform problems to solve

- Dispatch must balance proximity, eligibility, fairness, ETA, quality, price, availability, and privacy.
- Real-time state must remain consistent with the authoritative booking and payment systems.
- A delayed settlement model requires an immutable double-entry ledger, reconciliation, idempotent webhooks, and controlled dispute freezes.
- Identity documents and precise location are high-risk data requiring explicit retention and access controls.
- The product must grow from a modular monolith without coupling domains so tightly that extraction becomes unsafe.

## 4. Goals and non-goals

### 4.1 Business goals

- Establish a trustworthy marketplace with repeatable supply and demand in each launched service zone.
- Complete at least 90% of paid, provider-assigned bookings without platform-attributable failure after operational stabilization.
- Create transparent unit economics through configurable commission, fees, refunds, incentives, and payout rules.
- Build a defensible trust dataset based on verified bookings, skill evidence, completion evidence, behavioral signals, and dispute outcomes.
- Launch one city or tightly bounded set of zones first while using architecture that can scale horizontally and expand geographically.

### 4.2 User goals

- A customer can find eligible providers, choose one, pay, track, complete, review, and obtain support without leaving the product.
- An approved provider can go online, receive relevant requests, make an offer, navigate, prove service milestones, and receive an auditable payout.
- An administrator can verify providers, operate the marketplace, resolve financial exceptions, and monitor risk without database access.

### 4.3 Technical goals

- No invalid booking transitions or duplicate financial postings.
- Stateless API scaling, horizontally scalable real-time delivery, durable events, and replay-safe consumers.
- Observable critical journeys with measurable service-level objectives.
- Automated tests and deployment gates sufficient for frequent, low-risk releases.

### 4.4 Non-goals for MVP

- LocalServe will not employ every provider as an employee by default; worker classification requires jurisdiction-specific review.
- LocalServe will not provide lending, insurance underwriting, medical advice, or regulated emergency-response services.
- LocalServe will not operate a self-issued cash wallet or claim a legal escrow facility.
- LocalServe will not guarantee workmanship beyond explicitly published policy or protection-plan terms.
- LocalServe will not launch multi-country, multi-currency settlement in the MVP.
- AI will not autonomously approve identity verification, ban users, price emergencies, or decide disputes in the MVP.

## 5. Target users and stakeholders

### 5.1 Primary users

- Customers needing home, repair, maintenance, beauty, cleaning, vehicle, device, or other skilled local services.
- Independent professionals, technicians, small service businesses, and field-service teams.
- Marketplace operations, verification, finance, support, trust and safety, content, and platform administrators.

### 5.2 Internal and external stakeholders

| Stakeholder | Primary interest |
|---|---|
| Customer operations | Fulfillment, cancellation, rescheduling, satisfaction |
| Provider operations | Supply onboarding, availability, training, retention |
| Trust and safety | Identity, evidence, incident response, abuse prevention |
| Finance | Collection, reconciliation, tax, commission, refund, payout |
| Legal/compliance | Marketplace terms, privacy, identity, payments, worker classification |
| Product/design | Conversion, clarity, accessibility, retention |
| Engineering/SRE/security | Correctness, scalability, availability, security, recovery |
| Payment partner | KYC, fund flow, settlements, refunds, webhook integrity |
| Map/communication providers | Geocoding, routing, messaging, delivery reliability |
| Government/regulators | Applicable consumer, privacy, payment, tax, labor, and identity obligations |

## 6. User personas

### Persona C1 — Time-constrained household customer

**Profile:** Ananya, 31, works full time, uses UPI daily, and needs evening availability.  
**Goals:** Find a reliable provider quickly, know the likely price, avoid repeated calls, and receive an invoice.  
**Pain points:** No-shows, unclear charges, uncertain quality, and sharing her address too early.  
**Product needs:** Fast search, trusted profiles, comparable offers, scheduled booking, live ETA, chat, OTP milestones, protected payment, and clear support.

### Persona C2 — Safety-conscious customer

**Profile:** Meera, 58, books home services occasionally and prefers simple interfaces.  
**Goals:** See who is coming, understand each step, contact support, and use a familiar payment method.  
**Pain points:** Complex apps, small text, aggressive permissions, and ambiguous status.  
**Product needs:** Accessible UI, high-contrast mode, verified identity indicator, masked calling, explicit consent, readable timeline, caregiver-address option in a later release.

### Persona C3 — Emergency customer

**Profile:** Rahul, 27, has a late-night plumbing leak.  
**Goals:** Obtain the fastest eligible provider and see an honest emergency premium before payment.  
**Pain points:** False availability, price exploitation, and uncertain arrival.  
**Product needs:** Emergency category eligibility, capped/configured premium, fastest-arrival sorting, live tracking, and clear cancellation/fallback rules.

### Persona P1 — Independent technician

**Profile:** Imran, 34, AC technician with six years of experience.  
**Goals:** Receive nearby qualified leads, control working hours and radius, reduce payment risk, and build reputation.  
**Pain points:** Long unpaid travel, hidden commissions, cancellations, and delayed cash flow.  
**Product needs:** Online toggle, service radius, offer pricing/ETA, navigation, evidence upload, earnings ledger, payout tracking, and cancellation protection.

### Persona P2 — New provider with limited digital experience

**Profile:** Suman, 24, trained beautician using an entry-level Android device.  
**Goals:** Complete verification, manage a simple schedule, and understand next actions.  
**Pain points:** Document upload failures, complex forms, inconsistent connectivity.  
**Product needs:** Guided onboarding, resumable uploads, local-language readiness, offline indicators, clear rejection reasons, document renewal reminders.

### Persona A1 — Operations administrator

**Profile:** Priya, 29, handles verification and booking exceptions.  
**Goals:** Work from prioritized queues, make consistent decisions, and leave an audit trail.  
**Pain points:** Scattered tools, excessive data access, and unclear ownership.  
**Product needs:** Permission-scoped admin workspace, queues, evidence viewer, reason codes, dual control for sensitive actions, saved filters, and immutable audit logs.

### Persona A2 — Finance and dispute administrator

**Profile:** Arjun, 36, manages reconciliation, refunds, and provider payouts.  
**Goals:** Match gateway records to the internal ledger, resolve exceptions, and prevent duplicate money movement.  
**Pain points:** Manual spreadsheets, non-idempotent retries, and missing evidence.  
**Product needs:** Reconciliation dashboard, ledger views, frozen-balance controls, approval workflow, downloadable reports, and complete action history.

## 7. Business model

### 7.1 Primary revenue streams

1. **Platform commission:** A configurable percentage or fixed amount deducted from provider gross service value after eligible completion.
2. **Customer convenience fee:** A clearly disclosed fee where lawful and supported by the payment method and market policy.
3. **Emergency-service fee:** A configured premium with caps, transparent breakdown, and category/zone/time rules.
4. **Cancellation fee:** Applied only when published eligibility conditions are met; may compensate provider travel or reserved time.
5. **Provider subscription:** Future optional plan for enhanced analytics, scheduling, or lower commission; never required for baseline ranking.
6. **Sponsored placement:** Future clearly labeled advertising that cannot override safety or eligibility filters.
7. **Annual maintenance plans/service bundles:** Future prepaid or subscription offerings subject to financial and refund review.

### 7.2 Commission model

The MVP default commission is a configurable **15% of eligible service subtotal**, with a category-level range of 10%–25% subject to commercial and legal approval. The engine must support percentage, fixed, tiered, and promotional commission rules without code changes.

Commission is computed from a versioned pricing snapshot stored on the booking. Later catalog changes never retroactively alter a booking.

```text
serviceSubtotal = sum(lineItem.quantity × lineItem.unitPriceMinor)
customerPayable = serviceSubtotal + taxes + convenienceFee + emergencyFee - discounts - credits
commission = commissionRule(serviceSubtotal, category, providerTier, zone, effectiveAt)
providerNet = serviceSubtotal + providerEligibleFees - commission - providerTaxesWithheld - adjustments
```

Rounding occurs once per component using the currency's minor-unit rules. The complete breakdown must be visible before customer payment and in the provider earning statement.

### 7.3 Marketplace incentives

- Referral and loyalty credits are ledgered promotional liabilities with expiration and anti-abuse rules.
- Provider incentives are separate ledger entries; they must not silently change reported commission.
- Coupons declare funding source: platform, provider, co-funded, or campaign budget.
- Ranking must not penalize a provider for declining requests outside declared availability, radius, or skill eligibility.

### 7.4 Unit-economics measures

- Gross Transaction Value (GTV)
- Net revenue after refunds, incentives, gateway fees, taxes, and support credits
- Contribution margin per completed booking
- Customer acquisition cost and payback period
- Provider acquisition/onboarding cost
- Repeat booking rate and bookings per active customer
- Provider utilization, earnings per online hour, and supply retention
- Refund, dispute, chargeback, and support cost per booking

---

## 8. Release scope and prioritization

### 8.1 MVP — first public production release

The MVP includes the smallest complete, safe marketplace loop:

- Customer/provider registration, email/password, phone OTP, Google login, verification, recovery, session management, and strict RBAC.
- Provider onboarding, masked identity/document workflow, skill/service setup, bank/UPI payout setup, admin approval, and expiry tracking.
- Admin-managed categories, subcategories, services, price guidance, zones, configuration, and feature flags.
- Location-aware service discovery, provider profiles, filters, availability, ratings, and geospatial matching.
- Instant, scheduled, and emergency booking; provider offers; customer selection; strict booking state machine.
- Server-created payment order, verified webhook capture, internal held-payment ledger, provider allocation, start/completion OTPs, satisfaction confirmation, commission, wallet credit, payout, refund, and reconciliation.
- Real-time provider request, booking update, chat, read receipt, provider presence, and live location/ETA.
- In-app, push, email, and SMS notifications for critical events, with preferences, retry, and failed-delivery handling.
- Cancellations, rescheduling, disputes, evidence, payment freeze, admin resolution, support tickets, and audited actions.
- Verified review after completed booking, one review per booking, moderation/reporting, and aggregate ratings.
- Separate responsive customer, provider, and admin web experiences; mobile-ready contracts and shared design system.
- Security, observability, backup, recovery, automated tests, CI/CD, and production runbooks.

### 8.2 V1 Growth — committed post-launch scope

- Customer favorites, recently viewed providers, repeat booking, rich transaction export, loyalty, referrals, and expanded coupons.
- Provider performance analytics, richer schedules/breaks, service bundles, advanced price management, and provider teams.
- Promotional campaigns, homepage banners, CMS, bulk notifications, and advanced analytics/heatmaps.
- Voice/video integration through privacy-preserving providers, masked calling, WhatsApp integration, and more languages.
- OpenSearch/Elasticsearch migration when observed MongoDB search limits justify it.
- Expanded mobile applications, offline queues for field evidence, and background location optimization.

### 8.3 Future scope

- AI service recommendations, voice search, fraud signals, review moderation assistance, document triage, support assistant, smart matching, demand/supply prediction, route optimization, and dispute classification.
- Corporate accounts, subscriptions, advertisements, service auctions, provider bidding, surge pricing, SOS partnerships, protection plans, and multi-country support.
- Microservice extraction only when domain load, team ownership, deployment independence, or resilience evidence justifies it.

### 8.4 Prioritization rule

Safety, money correctness, authorization, privacy, booking integrity, evidence, and recoverability always outrank conversion experiments. Feature flags must default new high-risk behavior off and support immediate rollback.

---

## 9. Functional requirements

Every MVP requirement below is mandatory unless its release column states otherwise. Each implementation phase must maintain a traceability map from requirement ID to design, API, code, and test IDs.

### 9.1 Identity, authentication, authorization, and accounts

| ID | Release | Requirement |
|---|---|---|
| IAM-001 | MVP | The platform MUST provide separate customer registration, provider registration, and admin login entry points. Public admin registration MUST not exist. |
| IAM-002 | MVP | A user MUST be able to sign in using verified email/password, verified phone/OTP, or Google OAuth 2.0, subject to account status and role policy. |
| IAM-003 | MVP | Passwords MUST be hashed using Argon2id with centrally configured parameters, breached-password screening integration, strength rules, and safe migration of hash parameters. |
| IAM-004 | MVP | Access tokens MUST be short-lived signed JWTs containing only minimal identity/session claims. Refresh tokens MUST be opaque, hashed at rest, rotated on every use, and organized into token families. |
| IAM-005 | MVP | Reuse of a rotated refresh token MUST revoke the affected token family, record a security event, and require reauthentication on affected sessions. |
| IAM-006 | MVP | Users MUST be able to view named device sessions, revoke one session, securely log out the current session, and log out all sessions. |
| IAM-007 | MVP | Email and phone verification tokens MUST be single-use, expire, have attempt limits, and be stored only as protected/hashed values with no plaintext OTP logging. |
| IAM-008 | MVP | Forgot/reset-password flows MUST prevent account enumeration, use single-use expiring tokens, revoke existing refresh tokens after reset, and notify the user. |
| IAM-009 | MVP | Role checks and fine-grained permission checks MUST be enforced on the server. UI hiding is not authorization. |
| IAM-010 | MVP | Admin accounts MUST use separately scoped permissions; sensitive finance, role, export, and identity-document actions SHOULD require step-up authentication and MAY require dual approval. |
| IAM-011 | MVP | Optional TOTP-based admin 2FA MUST be available at launch and enforceable by policy for privileged admin roles. Recovery codes MUST be hashed and one-time use. |
| IAM-012 | MVP | Login, OTP, recovery, and token endpoints MUST use account, IP, device, and global rate limits with progressive backoff and brute-force detection. |
| IAM-013 | MVP | Authentication activity MUST record time, approximate IP-derived region, device/user-agent summary, outcome, reason code, session ID, and correlation ID without sensitive secrets. |
| IAM-014 | MVP | Suspended, banned, pending-deletion, and deleted accounts MUST be denied according to policy. A suspended provider MUST immediately stop receiving new requests. |
| IAM-015 | MVP | Customer account deletion MUST include confirmation, reauthentication, legal-hold exceptions, retention explanation, session revocation, and asynchronous erasure/anonymization workflow. |
| IAM-016 | MVP | Linking login methods MUST require proof of control and protect against account takeover caused by matching unverified email addresses. |
| IAM-017 | MVP | “Remember me” MUST extend only the refresh/session policy; it MUST NOT create a long-lived access token. |
| IAM-018 | V1 Growth | Risk-adaptive authentication SHOULD support impossible-travel, new-device, IP reputation, and behavior signals without making irreversible automated decisions. |

### 9.2 Customer profile and customer experience

| ID | Release | Requirement |
|---|---|---|
| CUS-001 | MVP | Customer onboarding MUST collect the minimum profile data, consent, locale, notification preferences, and optional default address. |
| CUS-002 | MVP | Customers MUST have a dedicated responsive dashboard showing active booking, relevant actions, recommended/popular services, recent activity, and unread notifications. |
| CUS-003 | MVP | Customers MUST be able to create, edit, label, select, and delete `HOME`, `WORK`, and `OTHER` addresses, subject to bookings and retention policy. |
| CUS-004 | MVP | Address selection MUST show location accuracy, map pin, serviceability, and an editable customer instruction without exposing it to unselected providers. |
| CUS-005 | MVP | Customers MUST be able to view active, upcoming, completed, cancelled, refunded, and disputed booking histories with timeline and money breakdown. |
| CUS-006 | MVP | Customers MUST receive and download a uniquely numbered invoice/tax invoice when applicable, generated from immutable booking and tax snapshots. |
| CUS-007 | MVP | Customers MUST be able to configure channel-level notification preferences, while security and legally required transactional notices remain non-optional. |
| CUS-008 | MVP | Customers MUST be able to request cancellation, rescheduling, refund, dispute, and account deletion using explicit reason codes and policy previews. |
| CUS-009 | V1 Growth | Customers SHOULD be able to favorite providers, view recently viewed providers, repeat eligible bookings, use referrals/loyalty, and export transaction history. |

### 9.3 Provider onboarding, verification, and lifecycle

| ID | Release | Requirement |
|---|---|---|
| PRV-001 | MVP | Provider onboarding MUST be resumable and collect identity, contact, service areas, skills, experience, certificates, availability, payout, tax, and consent data in stages. |
| PRV-002 | MVP | A provider MUST not become discoverable, receive requests, or withdraw funds until the required verification and admin approval are complete. |
| PRV-003 | MVP | The verification workflow MUST accept masked Aadhaar/offline-verification evidence where approved, PAN, driving licence when relevant, selfie, certificates, and other category-specific documents. |
| PRV-004 | MVP | Each document MUST record type, issuing country/authority where relevant, masked identifier, issue/expiry dates, verification status, rejection reason, storage object key, checksum, and reviewer history. |
| PRV-005 | MVP | Identity documents MUST be kept in private encrypted object storage and displayed only through short-lived, purpose-bound signed URLs to authorized reviewers. |
| PRV-006 | MVP | The admin MUST be able to approve, reject with standardized and human-readable reason, or request additional information at document or application level. |
| PRV-007 | MVP | Provider approval, rejection, suspension, reactivation, and verification expiry MUST notify the provider and generate audit events. |
| PRV-008 | MVP | The system MUST support integration adapters for face matching, background verification, and police verification without making them mandatory or pretending an unintegrated check occurred. |
| PRV-009 | MVP | The provider MUST be able to manage skills, offered services, experience, certificates, service radius, profile summary, and permitted pricing fields, with reapproval for risk-sensitive changes. |
| PRV-010 | MVP | The provider MUST control online/offline status, working hours, availability slots, and breaks. Going online requires approval, current serviceability, and required location permission. |
| PRV-011 | MVP | Providers MUST receive document-expiry warnings at configurable intervals and MUST be restricted from affected categories after a required document expires. |
| PRV-012 | MVP | Providers MUST be able to manage verified bank and UPI payout destinations. Changing a destination MUST use reauthentication, cooling-off policy, notifications, and verification. |
| PRV-013 | MVP | Provider profile views MUST reveal verification badges and evidence categories but MUST NOT reveal identity-document numbers, home address, bank data, or private contact data. |
| PRV-014 | MVP | Providers MUST have a support/appeal path for rejection, suspension, customer issue, payout exception, and document decision. |

### 9.4 Provider operations and earnings

| ID | Release | Requirement |
|---|---|---|
| OPS-001 | MVP | Providers MUST have a dedicated dashboard showing availability, current/next booking, incoming offers, required actions, document alerts, earnings, and support notices. |
| OPS-002 | MVP | Each provider request MUST show service, approximate distance/zone, requested time/type, customer rating summary if policy permits, required skills/equipment, and price guidance without prematurely exposing exact address/contact. |
| OPS-003 | MVP | Providers MUST be able to accept with an estimated price and ETA, reject with optional reason, or allow the offer to expire. |
| OPS-004 | MVP | Provider acceptance MUST be atomic and idempotent. It creates an offer, not a final assignment; the customer makes the selection. |
| OPS-005 | MVP | The provider MUST see current, upcoming, and historical bookings and a status-specific action checklist. |
| OPS-006 | MVP | After provider assignment, the provider MUST receive navigation coordinates under a purpose-bound access policy and may use external navigation. |
| OPS-007 | MVP | The provider MUST enter the Start OTP before work begins and the Completion OTP at the configured completion step; both are server-verified and attempt-limited. |
| OPS-008 | MVP | The provider MUST be able to upload before-service and after-service evidence with consent/status indicators and category-specific requirements. |
| OPS-009 | MVP | Providers MUST see gross amount, fees, commission, tax, adjustments, held amount, available balance, pending payout, and paid amount for every earning. |
| OPS-010 | MVP | Providers MUST be able to submit a withdrawal request to a verified destination, subject to available balance, reserve/freeze, minimum amount, risk controls, and approval policy. |
| OPS-011 | MVP | Provider analytics MUST show today/week/month/total earnings, acceptance, completion, cancellation, rating, response time, and trend definitions based on authoritative events. |
| OPS-012 | MVP | Providers MUST be able to raise an issue against a customer with evidence; safety-critical issues must route to an urgent queue. |

### 9.5 Catalog, pricing, discovery, and search

| ID | Release | Requirement |
|---|---|---|
| CAT-001 | MVP | Admins MUST manage versioned categories, subcategories, services, requirements, icon/media, service duration guidance, pricing model, taxes, cancellation rules, and launch zones. |
| CAT-002 | MVP | Catalog records MUST support draft, scheduled publication, published, archived, and locale-ready content without deleting history used by existing bookings. |
| SRC-001 | MVP | Customers MUST search by service name, category, subcategory, and location with typo-tolerant normalized matching and safe query limits. |
| SRC-002 | MVP | Search MUST support price, rating, availability, experience, distance, and service filters plus relevance, rating, price, distance, and fastest-arrival sorting. |
| SRC-003 | MVP | Nearby provider discovery MUST query only approved, online, eligible, non-suspended providers with current location, matching skills, service radius, capacity, and applicable zone. |
| SRC-004 | MVP | Provider location MUST use a MongoDB GeoJSON `Point` with a `2dsphere` index; precise coordinates MUST not be returned in public search results. |
| SRC-005 | MVP | Search results MUST provide transparent labels for promoted content, estimated price, rating count, experience, approximate distance, next availability, and verification status. |
| SRC-006 | MVP | The system MUST record privacy-aware search analytics and support popular services, trending categories, recent searches, and suggestions with deletion controls. |
| SRC-007 | MVP | Empty, stale-location, no-provider, and service-unavailable results MUST offer safe alternatives such as schedule, widen radius within policy, select another service, or notify when available. |
| SRC-008 | Future | Search contracts and indexing events MUST permit migration to OpenSearch/Elasticsearch without changing consumer-facing resource models. |

### 9.6 Location, serviceability, and tracking

| ID | Release | Requirement |
|---|---|---|
| LOC-001 | MVP | Location permission MUST be requested in context, explain purpose, support denial, and permit manual address entry. |
| LOC-002 | MVP | The map abstraction MUST support autocomplete, geocoding, reverse geocoding, route, distance matrix/ETA, map display, and provider substitution. |
| LOC-003 | MVP | Serviceability MUST be determined server-side using configured zones, category rules, provider radius, and geospatial validation. |
| LOC-004 | MVP | Provider location updates MUST include coordinates, horizontal accuracy, observed time, sequence, device/session, and spoofing-risk metadata; stale or low-accuracy updates must be labeled. |
| LOC-005 | MVP | Exact customer location becomes available only to the selected provider after policy conditions are met; unselected providers receive an approximate zone/distance. |
| LOC-006 | MVP | Live tracking MUST begin no earlier than provider assignment and end automatically at cancellation, completion/closure, or configured timeout. |
| LOC-007 | MVP | ETA MUST show a freshness timestamp and communicate when the map provider is unavailable rather than inventing an estimate. |
| LOC-008 | MVP | Location history MUST have configurable short retention, access logging, deletion/anonymization, legal-hold handling, and no use for unrelated advertising without separate consent. |
| LOC-009 | V1 Growth | Geofencing SHOULD detect probable arrival/departure but MUST NOT automatically start or complete a service without the required verification. |

### 9.7 Booking, provider dispatch, and state machine

| ID | Release | Requirement |
|---|---|---|
| BKG-001 | MVP | Customers MUST create `INSTANT`, `SCHEDULED`, or `EMERGENCY` booking requests with service, address, timing, problem details, media, and price acknowledgement. |
| BKG-002 | MVP | Booking creation MUST persist a catalog, pricing-rule, tax, policy, zone, and address snapshot so later edits cannot change the agreed contract. |
| BKG-003 | MVP | The dispatch engine MUST find eligible providers using approval, status, skill, zone/radius, schedule, capacity, location freshness, category requirements, and risk exclusions. |
| BKG-004 | MVP | Dispatch SHOULD rank by ETA, skill fit, reliability, customer choice signals, fair exposure, price guidance, and recent workload; the factors and weights must be versioned and auditable. |
| BKG-005 | MVP | A request MUST be broadcast to a bounded cohort, expand in controlled waves, expire, and avoid repeatedly spamming the same provider. |
| BKG-006 | MVP | Interested-provider offers MUST include provider, price estimate/breakdown, ETA, expiry, and applicable conditions. |
| BKG-007 | MVP | The customer MUST compare interested providers using a consistent presentation of rating, review volume, verified skills, experience, ETA, and estimated price. |
| BKG-008 | MVP | Provider selection MUST be atomic; the selected offer changes to `SELECTED_BY_CUSTOMER`, other active offers become `NOT_SELECTED` or `EXPIRED`, and all parties receive updates. |
| BKG-009 | MVP | The booking MUST follow only allowed state-machine transitions with actor, preconditions, authorization, idempotency, timestamp, correlation ID, and append-only status history. |
| BKG-010 | MVP | Payment capture and verified webhook confirmation are required before provider assignment under the standard prepay policy. Frontend success is never sufficient. |
| BKG-011 | MVP | Start OTP MUST be generated only for the assigned booking at the correct state, delivered to the authorized customer channel, expire, be attempt-limited, and be stored protected/hashed. |
| BKG-012 | MVP | Completion OTP MUST be separate from the Start OTP and may be generated only when the booking is in the configured completion stage. |
| BKG-013 | MVP | OTP verification MUST bind booking, purpose, user, issuance version, expiration, and attempt counter to prevent cross-booking or replay use. |
| BKG-014 | MVP | The platform MUST support policy-driven customer/provider/admin cancellation with fees, payout/refund effects, reason codes, evidence, and status-specific eligibility. |
| BKG-015 | MVP | Scheduled bookings MUST support policy-compliant rescheduling with provider re-confirmation or controlled redispatch when necessary. |
| BKG-016 | MVP | When the customer does not confirm satisfaction within a configured window, the system MUST follow a disclosed auto-confirmation or manual-review rule; high-risk/disputed cases never auto-release. |
| BKG-017 | MVP | Booking close MUST require terminal service/payment conditions, invoice generation outcome, ledger consistency, and no unresolved dispute or workflow hold. |
| BKG-018 | MVP | Every material state change MUST publish a versioned domain event through a transactional outbox and be safe to replay. |

### 9.8 Pricing, payment, held funds, wallet, refund, and payout

| ID | Release | Requirement |
|---|---|---|
| PAY-001 | MVP | Pricing MUST calculate server-side from versioned line items, rule snapshots, fees, discounts, credits, tax, and rounding; the client may display but never authoritatively calculate the payable total. |
| PAY-002 | MVP | Payment-order creation MUST require an idempotency key, authenticated booking ownership, correct state, current quote, currency, amount, and unexpired payment attempt. |
| PAY-003 | MVP | Razorpay, Stripe, wallet/credit, and future gateways MUST implement a common port while preserving gateway-specific capabilities and identifiers. |
| PAY-004 | MVP | Webhook endpoints MUST read the raw request body, verify the provider signature and timestamp policy, deduplicate event IDs, persist receipt, and respond safely to retries. |
| PAY-005 | MVP | The platform MUST treat a payment as captured only after server-to-server verification through a valid webhook and/or authenticated provider API reconciliation. |
| PAY-006 | MVP | A captured customer payment MUST create balanced, immutable double-entry ledger postings that distinguish gateway receivable/cash, customer liability/held funds, fees, tax, platform revenue, provider payable, refunds, and adjustments. |
| PAY-007 | MVP | Ledger entries MUST be append-only; corrections use compensating entries. Administrators cannot edit or delete posted entries. |
| PAY-008 | MVP | Payment release MUST require completion verification, customer confirmation or approved timeout policy, no active dispute/freeze, required settlement delay, successful reconciliation, and eligible provider status. |
| PAY-009 | MVP | A dispute MUST atomically freeze the releasable balance and related payout. Any already-settled shortfall becomes a risk/receivable case, not a silent negative mutation. |
| PAY-010 | MVP | Full and partial refunds MUST reference original payment, booking, reason, approving actor, gateway result, ledger postings, and remaining refundable amount. |
| PAY-011 | MVP | Provider wallet balances MUST be derived from ledger postings and expose `pending`, `available`, `frozen`, and `paidOut` views; cached balances must be reconcilable to the ledger. |
| PAY-012 | MVP | Customer promotional credits MUST be non-withdrawable, rule-bound, expiring where stated, separately ledgered, and applied in deterministic order. |
| PAY-013 | MVP | Withdrawal creation and approval MUST use idempotency, verified payout destination, available balance, limits, risk rules, optional cooling period, and dual control above configured thresholds. |
| PAY-014 | MVP | Daily automated reconciliation MUST compare bookings, payment attempts, gateway payments/refunds/transfers/settlements, ledger postings, payouts, and bank/gateway reports; exceptions enter owned queues. |
| PAY-015 | MVP | Payment APIs and admin views MUST mask sensitive information and never expose gateway secrets, bank account numbers, UPI PINs, raw card data, or webhook secrets. |
| PAY-016 | MVP | Fees, tax, commission, discount, refund, and payout rules MUST be effective-dated, versioned, scoped by zone/category, and copied to the financial snapshot. |
| PAY-017 | MVP | Failed or abandoned payments MUST support safe retry with a new attempt while preventing duplicate capture and double allocation. |
| PAY-018 | MVP | Chargeback and reversal events MUST be ingestible, freeze relevant balances when possible, and create finance/risk cases with complete evidence. |

### 9.9 Real-time communication, presence, and chat

| ID | Release | Requirement |
|---|---|---|
| RT-001 | MVP | Authenticated clients MUST connect to Spring WebSocket/STOMP using a session-bound access mechanism; destination subscriptions require server authorization. |
| RT-002 | MVP | Booking commands remain REST/domain commands. WebSocket events inform clients and MUST NOT bypass authoritative state transitions. |
| RT-003 | MVP | Real-time events MUST include event ID, type, version, occurred time, aggregate ID, aggregate version, correlation ID, and minimal authorized payload. |
| RT-004 | MVP | Redis MUST coordinate short-lived presence and WebSocket fan-out across instances; Kafka remains the durable event backbone for business events. |
| RT-005 | MVP | Customer and assigned provider MUST have a booking-scoped conversation after confirmation, with sequence ordering, delivery status, read receipts, and attachment controls. |
| RT-006 | MVP | Typing indicators and presence are ephemeral, privacy-minimized, rate-limited, and not retained as durable chat history. |
| RT-007 | MVP | Chat MUST prevent unassigned users and unauthorized admins from reading messages; approved dispute access must be purpose-bound, logged, and visible in policy. |
| RT-008 | MVP | Contact details MUST remain masked until confirmed booking and the configured disclosure condition. Voice-call UI MUST use a privacy-preserving provider or explicit consent. |
| RT-009 | MVP | Live location events MUST be sequence-checked, rate-limited, stale-aware, and scoped only to authorized booking participants and monitoring permissions. |
| RT-010 | MVP | Clients MUST recover missed events by refetching authoritative REST resources after reconnect; the system does not assume perfect socket delivery. |
| RT-011 | MVP | Abuse reporting, blocking after a booking, message retention, attachment scanning, and support escalation MUST be available. |

### 9.10 Notifications

| ID | Release | Requirement |
|---|---|---|
| NTF-001 | MVP | The notification domain MUST support in-app, FCM mobile/web push, email, and SMS through provider adapters. WhatsApp remains integration-ready. |
| NTF-002 | MVP | Templates MUST be versioned, locale-ready, channel-specific, previewable, approval-controlled, and protect against injection or accidental sensitive-data disclosure. |
| NTF-003 | MVP | Delivery MUST be asynchronous with idempotent jobs, retry/backoff, provider timeout/circuit breaker, delivery receipt handling, and a dead-letter queue. |
| NTF-004 | MVP | Security, OTP, booking, payment, refund, dispute, payout, and verification notifications MUST use defined urgency, channel fallback, and redaction rules. |
| NTF-005 | MVP | Users MUST be able to mark in-app notifications read/unread and manage preferences by category/channel, except mandatory security/legal communications. |
| NTF-006 | MVP | Promotional messages MUST require appropriate consent, quiet-hour and frequency controls, unsubscribe, campaign budget/audience validation, and admin approval. |
| NTF-007 | MVP | Bulk sends MUST use bounded batches, rate controls, scheduling, cancellation before dispatch, metrics, and audience snapshots. |
| NTF-008 | MVP | Failed notification queues and provider health MUST be visible to authorized administrators without exposing message secrets. |

### 9.11 Reviews, ratings, and provider ranking

| ID | Release | Requirement |
|---|---|---|
| REV-001 | MVP | Only the booking customer may create one review per eligible completed/closed booking. The review is automatically marked verified. |
| REV-002 | MVP | A review MUST support a 1–5 integer rating, optional text, approved image attachments, locale, moderation status, and booking/provider linkage. |
| REV-003 | MVP | Review creation MUST enforce the configured submission window; editing is allowed only within a configured period and retains version history. |
| REV-004 | MVP | Customers/providers MUST be able to report reviews with reason and evidence; admins may hide/remove content only with a reason and audit trail. |
| REV-005 | MVP | Provider aggregates MUST include count, mean, Bayesian/weighted score, and rating distribution and be recomputable from eligible reviews. |
| REV-006 | MVP | Ranking MUST use the weighted score and reliability/availability factors while controlling cold-start disadvantage and preventing payment for unlabeled organic rank. |
| REV-007 | MVP | Review images MUST pass type, size, metadata stripping, safety scan, and access/transformation controls. |
| REV-008 | Future | Fraud-detection assistance MAY flag coordinated, duplicate, incentivized, or anomalous reviews, but human review is required for adverse action. |

### 9.12 Disputes, safety, and support

| ID | Release | Requirement |
|---|---|---|
| DSP-001 | MVP | An eligible participant MUST raise a dispute within the published window using a category, statement, requested outcome, and optional evidence. |
| DSP-002 | MVP | Opening a qualifying dispute MUST freeze the unreleased financial amount and payout through one idempotent domain workflow. |
| DSP-003 | MVP | Evidence MUST be append-only, timestamped, checksummed, privately stored, malware-scan-ready, and attributable; replacement creates a new version. |
| DSP-004 | MVP | Case timelines MUST include booking statuses, OTP results, location summaries, allowed chat evidence, payment/ledger history, attachments, notices, actions, and deadlines. |
| DSP-005 | MVP | Admins MUST record external comments separately from internal notes. Internal notes require stricter permission and must never be sent accidentally. |
| DSP-006 | MVP | Resolution MUST support full refund, partial refund, or release using reason codes, calculated amount boundaries, approval policy, compensating ledger entries, and notices. |
| DSP-007 | MVP | High-value or sensitive resolutions MUST require maker-checker approval. The same admin cannot both propose and approve above the configured threshold. |
| DSP-008 | MVP | Appeals MUST preserve the original resolution and create a linked review stage; reopening never erases prior evidence or ledger history. |
| SUP-001 | MVP | Customers and providers MUST create support tickets linked to account/booking/payment when relevant, with priority, category, SLA, assignment, conversation, and attachments. |
| SUP-002 | MVP | Safety-critical reports MUST show an emergency disclaimer, provide local emergency-contact guidance where applicable, and enter a 24/7 urgent operations queue if that operational coverage exists. |
| SUP-003 | MVP | Support agents MUST have least-privilege views and cannot perform finance or identity actions without the corresponding permission/workflow. |

### 9.13 Coupons, referrals, and loyalty

| ID | Release | Requirement |
|---|---|---|
| CUP-001 | MVP | Authorized admins MUST create, preview, schedule, pause, and expire versioned coupon campaigns with code, scope, funding source, benefit, caps, budget, time window, and usage limits. |
| CUP-002 | MVP | Coupon eligibility and discount amount MUST be calculated server-side from customer, service, category, zone, booking type, payment method, minimum value, prior usage, and current campaign version. |
| CUP-003 | MVP | Coupon reservation/consumption MUST be concurrency-safe and idempotent; failed/expired payments release reservations according to policy without enabling duplicate use. |
| CUP-004 | MVP | Platform-, provider-, and co-funded discounts MUST create distinct pricing and ledger components and remain visible in booking/invoice/earnings views as applicable. |
| CUP-005 | MVP | Coupon abuse controls MUST include per-user/device/payment/address/referral limits, global budget enforcement, safe velocity rules, and reviewable reason codes. |
| CUP-006 | V1 Growth | Referral codes SHOULD create a relationship only after eligibility checks and award each party through separately ledgered, rule-bound promotional credits after a qualifying event. |
| CUP-007 | V1 Growth | Loyalty rewards SHOULD use a versioned earn/redeem/expire policy, immutable transaction history, tier calculation, anti-abuse controls, and customer-visible expiry. |
| CUP-008 | V1 Growth | Customers SHOULD view eligible offers, coupon application reason, credit/reward balance, pending/earned/expired history, and terms before checkout. |

### 9.14 Admin, content, configuration, and governance

| ID | Release | Requirement |
|---|---|---|
| ADM-001 | MVP | The admin application MUST be deployed and routed separately from customer/provider experiences and have no public signup. |
| ADM-002 | MVP | Admin roles and permissions MUST cover user, provider, verification, booking, finance, dispute, support, content, campaign, audit, monitoring, settings, and admin-management boundaries. |
| ADM-003 | MVP | Admins MUST search/filter users, providers, bookings, payments, disputes, tickets, and logs using masked results, export controls, and purpose-aware access. |
| ADM-004 | MVP | Account suspend/ban/unban actions MUST require reason, duration where applicable, evidence/reference, impact preview, notification policy, and audit event. |
| ADM-005 | MVP | Role/permission changes, privileged exports, feature-flag changes, payment adjustments, payout approvals, and sensitive-document access MUST be fully audited. |
| ADM-006 | MVP | System settings MUST be typed, validated, effective-dated when financial/operational, versioned, permission-controlled, and safely cached with invalidation. |
| ADM-007 | MVP | Feature flags MUST have owner, purpose, environments, audience rule, default, expiry/review date, change history, and emergency kill switch. |
| ADM-008 | MVP | Admin dashboards MUST expose platform health, failed jobs, reconciliation exceptions, notification failures, queue lag, booking anomalies, and critical alerts with links to owned runbooks. |
| ADM-009 | V1 Growth | CMS MUST manage static pages, FAQs, localized content, homepage sections, and banners with draft/preview/approval/scheduling. |
| ADM-010 | V1 Growth | Campaign tools MUST support audience rules, suppression lists, consent, budget, scheduling, frequency, experiment labels, and outcome analytics. |

### 9.15 Analytics, audit, and reporting

| ID | Release | Requirement |
|---|---|---|
| ANL-001 | MVP | The system MUST emit privacy-minimized, versioned analytics events for acquisition, search, offer, booking, payment, fulfillment, review, dispute, and retention funnels. |
| ANL-002 | MVP | Admin analytics MUST provide daily/weekly/monthly/yearly revenue, GTV, commission, payouts, refunds, active users/providers, growth, top services/categories/providers, and booking outcomes. |
| ANL-003 | MVP | Operational KPIs MUST include acceptance, completion, cancellation, dispute, refund, payment failure, response, arrival, service duration, notification delivery, repeat booking, and retention. |
| ANL-004 | MVP | Metric definitions MUST be versioned in a data dictionary specifying numerator, denominator, cohort, timezone, exclusions, owner, and freshness. |
| ANL-005 | MVP | Financial dashboards MUST reconcile to the ledger; eventually consistent analytics must show last refresh and must not be used as the source for money movement. |
| ANL-006 | V1 Growth | Geographic demand/supply heatmaps MUST aggregate or blur locations sufficiently to prevent reidentification and must not expose a provider's precise history. |
| AUD-001 | MVP | Security, admin, booking, verification, dispute, and financial audit events MUST be append-only, tamper-evident, queryable by authorized staff, and retained by policy. |
| AUD-002 | MVP | Audit records MUST include actor type/id, impersonation context if any, action, target, before/after diff with redaction, reason, time, IP/device summary, correlation ID, and outcome. |
| AUD-003 | MVP | Normal application users and admins MUST not be able to delete or edit immutable audit/ledger entries. Retention disposal must be controlled and separately audited. |

### 9.16 File management

| ID | Release | Requirement |
|---|---|---|
| FIL-001 | MVP | File upload MUST use short-lived upload authorization, server-side ownership validation, allowed type/size/count rules, checksum, and post-upload verification. |
| FIL-002 | MVP | The system MUST validate file signatures, not only extensions or client MIME types, and must support malware-scanning quarantine before sensitive files become reviewable. |
| FIL-003 | MVP | Image processing MUST strip unnecessary metadata, generate approved variants, limit decompression bombs, and avoid public origin URLs for private content. |
| FIL-004 | MVP | Downloads of private files MUST use short-lived signed URLs or streamed authorization, purpose checks, access logs, and safe content-disposition headers. |
| FIL-005 | MVP | Deleting a logical record MUST not orphan files. File retention, legal hold, archival, and cryptographic erasure workflows must be explicit and retryable. |

---

## 10. Non-functional requirements

### 10.1 Scale and capacity envelope

The initial production deployment may be smaller, but architecture and data contracts must not prevent the following planning envelope:

| Metric | Design envelope |
|---|---:|
| Registered users | 20 million |
| Monthly active users | 5 million |
| Approved providers | 1 million |
| Concurrent authenticated users | 250,000 |
| Concurrent WebSocket connections | 150,000 across horizontally scaled nodes |
| Booking creation peak | 1,000 requests/second |
| Provider location peak | 50,000 updates/second before adaptive sampling/aggregation |
| Notification burst | 250,000 messages/minute through queued delivery |
| Historical bookings | 1 billion with archival/sharding readiness |

These are engineering planning targets, not launch forecasts. Phase 2 must show how partitions, caching, aggregation, backpressure, and staged growth prevent unnecessary Day-1 cost.

### 10.2 Availability and service levels

| ID | Requirement / target |
|---|---|
| NFR-AVL-001 | Core authenticated API and booking read availability target: 99.95% monthly, excluding published maintenance. |
| NFR-AVL-002 | Payment webhook ingestion target: 99.99% monthly, with durable buffering/retry and no dependency on frontend availability. |
| NFR-AVL-003 | Admin/content analytics may target 99.9% but failure must not affect active bookings or payment processing. |
| NFR-AVL-004 | Readiness/liveness checks must distinguish dependency degradation from process failure and avoid restart storms. |
| NFR-AVL-005 | Critical third-party degradation must produce a defined fallback: queue, retry, alternate provider, manual action, or clear user message. |

### 10.3 Performance targets

All targets are measured server-side at steady-state production-like load, excluding external provider latency where separately stated.

| Operation | Target |
|---|---:|
| Cached/public catalog read | p95 ≤ 200 ms, p99 ≤ 500 ms |
| Authenticated resource read | p95 ≤ 300 ms, p99 ≤ 800 ms |
| Standard command write | p95 ≤ 500 ms, p99 ≤ 1.2 s |
| Nearby-provider candidate query | p95 ≤ 500 ms within configured maximum radius |
| Booking dispatch first cohort created | p95 ≤ 2 s after committed request |
| Real-time booking update delivery | p95 ≤ 1 s after committed outbox publication |
| Chat message accepted | p95 ≤ 400 ms; recipient delivery p95 ≤ 1 s when connected |
| Payment webhook acknowledgement | p95 ≤ 500 ms after verification and durable receipt; downstream processing asynchronous |
| Customer web Core Web Vitals | LCP ≤ 2.5 s, INP ≤ 200 ms, CLS ≤ 0.1 at 75th percentile on supported mobile devices/network profile |

### 10.4 Reliability and data integrity

| ID | Requirement |
|---|---|
| NFR-REL-001 | Financial posting, booking transitions, OTP consumption, offer selection, and payout commands must be idempotent. |
| NFR-REL-002 | Kafka delivery is at-least-once; consumers must deduplicate using event/message IDs and persist inbox state where side effects occur. |
| NFR-REL-003 | Domain changes and event publication must use a transactional outbox compatible with MongoDB transactions on a replica set. |
| NFR-REL-004 | Distributed locks may serialize short critical sections but cannot be the sole source of correctness; database predicates/versioning must enforce invariants. |
| NFR-REL-005 | Retry policies must be bounded, jittered, observable, and limited to safe/idempotent operations. Poison messages must reach a dead-letter workflow. |
| NFR-REL-006 | Every external request must use connection/read timeouts, circuit breaking, and bulkhead isolation appropriate to the dependency. |
| NFR-REL-007 | The system must support graceful shutdown: stop new work, drain requests, close sockets with reconnect guidance, finish/hand off jobs, and commit offsets safely. |

### 10.5 Security and privacy

| ID | Requirement |
|---|---|
| NFR-SEC-001 | TLS is mandatory for all public and service communication; internal service identity/mTLS is deployment-ready. |
| NFR-SEC-002 | Spring Security must set CSP, HSTS, frame restrictions, referrer policy, permissions policy, MIME sniffing protection, and secure CORS. CSP must use nonces/hashes rather than broad unsafe exceptions. |
| NFR-SEC-003 | Cookie-based refresh/session flows require `Secure`, `HttpOnly`, appropriate `SameSite`, CSRF tokens, origin checks, and rotation. Bearer-token flows must avoid browser storage that exposes long-lived secrets. |
| NFR-SEC-004 | All input is schema-validated; Mongo queries use typed repository criteria and server-owned field allowlists to prevent operator injection/mass assignment. |
| NFR-SEC-005 | Secrets are supplied through a secrets manager/environment injection, rotated, scoped per environment, never committed, and validated at startup without logging values. |
| NFR-SEC-006 | Restricted data is encrypted using managed KMS envelope encryption where field-level isolation is required. Keys are rotated and access is audited. |
| NFR-SEC-007 | Logs, metrics, traces, errors, analytics, and events must use redaction rules and may not contain passwords, tokens, OTPs, full identity/payment data, or private document URLs. |
| NFR-SEC-008 | Every release must pass SAST, software-composition analysis, secret scanning, IaC scanning, container scanning, and critical/high vulnerability policy gates with documented exceptions. |
| NFR-SEC-009 | Independent penetration testing and OWASP ASVS-aligned verification are required before public launch and after material payment/authentication changes. |
| NFR-SEC-010 | Security events need severity, detection source, alert routing, triage runbook, containment, evidence preservation, notification decision, and post-incident review. |
| NFR-PRI-001 | Data collection must be purpose-limited and consent/notice must be understandable at the point of collection. Optional permissions cannot be bundled with core acceptance. |
| NFR-PRI-002 | Data-subject requests for access, correction, consent withdrawal, grievance, and erasure must be authenticated, tracked, time-bounded, and auditable. |
| NFR-PRI-003 | Precise location, identity files, chat, and financial data need separate retention schedules and role/purpose restrictions. |
| NFR-PRI-004 | Production data must not be copied into development/test. Synthetic or irreversibly de-identified data is required. |

### 10.6 Accessibility and inclusive UX

| ID | Requirement |
|---|---|
| NFR-A11Y-001 | Customer, provider, and admin web apps must conform to WCAG 2.2 AA for supported journeys. |
| NFR-A11Y-002 | All functions must support keyboard navigation, visible focus, semantic landmarks, labels, error summaries, screen-reader announcements, and no keyboard traps. |
| NFR-A11Y-003 | Text and interactive contrast must meet AA; meaning cannot depend only on color; light/dark/high-contrast modes must remain legible. |
| NFR-A11Y-004 | Animations respect `prefers-reduced-motion`; shimmer/skeleton states cannot create flashing or block assistive technology. |
| NFR-A11Y-005 | Touch targets must be appropriately sized and critical OTP/payment/status information must remain usable at 200% zoom. |
| NFR-A11Y-006 | Forms preserve entered values after recoverable errors, identify specific fields, provide suggestions, and never rely only on toast messages. |

### 10.7 Compatibility and client support

- Responsive web: current and previous two major versions of Chrome, Edge, Firefox, and Safari; current Android WebView baseline defined during Phase 6.
- Mobile architecture: Android-first for India, iOS supported; graceful behavior on low-memory devices and intermittent networks.
- APIs: additive backward-compatible changes within `/api/v1`; breaking changes require `/api/v2` or explicit versioned media/events and a published deprecation window.
- Event consumers must ignore unknown additive fields and reject incompatible major schema versions to a controlled workflow.
- Timestamps, money, phone, locale, IDs, and coordinates follow Section 1.2 everywhere.

### 10.8 Observability and operations

| ID | Requirement |
|---|---|
| NFR-OBS-001 | All services emit structured JSON logs with timestamp, level, service, environment, trace/correlation IDs, event name, safe actor/aggregate IDs, and redacted context. |
| NFR-OBS-002 | OpenTelemetry traces cover inbound API/WebSocket, database, Redis, Kafka, payment/map/notification calls, and background jobs with sampling that preserves errors and critical finance flows. |
| NFR-OBS-003 | RED metrics (rate, errors, duration), saturation, JVM, MongoDB, Redis, Kafka, WebSocket, queue, payment, and business SLIs must have dashboards and owned alerts. |
| NFR-OBS-004 | Alerts must be actionable, severity-classified, deduplicated, linked to runbooks, and tuned to reduce noise. Critical money/auth failures page on-call. |
| NFR-OBS-005 | Synthetic checks must exercise login health, catalog, booking-read, payment-webhook reachability, and admin health without making real financial transactions. |
| NFR-OBS-006 | Audit logs and observability logs are separate: application operators cannot alter audit evidence through log-retention controls. |

### 10.9 Backup, recovery, and continuity

| Data/system | Target RPO | Target RTO | Minimum control |
|---|---:|---:|---|
| Financial ledger/payments | ≤ 5 minutes | ≤ 30 minutes | Continuous replication/PITR, immutable backups, reconciliation replay |
| Booking/identity operational data | ≤ 15 minutes | ≤ 60 minutes | PITR plus cross-AZ replica and tested restore |
| Redis ephemeral data | Data-class-specific | ≤ 30 minutes | Reconstruct from Mongo/Kafka where possible; persistent config for required metadata |
| Object evidence/documents | ≤ 15 minutes | ≤ 2 hours | Versioning, replication, lifecycle protection, restore test |
| Analytics/search derivatives | ≤ 24 hours | ≤ 24 hours | Rebuild from durable operational/event sources |

Backups must be encrypted, access-controlled, integrity-checked, restoration-tested at least quarterly, and protected from the same credentials used by the running application. Disaster-recovery exercises must document actual RPO/RTO results and corrective actions.

### 10.10 Maintainability and engineering quality

| ID | Requirement |
|---|---|
| NFR-ENG-001 | Domain business logic must not live in controllers, Mongo documents, UI components, or gateway adapters. |
| NFR-ENG-002 | API DTOs, domain models, and persistence documents are separate; Mongo documents are never returned directly. |
| NFR-ENG-003 | Module boundaries are enforced using Maven modules and ArchUnit; cross-domain access uses application ports/events, not internal repositories. |
| NFR-ENG-004 | Code must pass formatting, static analysis, null-safety conventions, compiler warnings policy, architecture tests, and review. |
| NFR-ENG-005 | Every externally visible command/event/API has documentation, validation, authorization, error contract, telemetry, and tests. |
| NFR-ENG-006 | Database/index migrations must be versioned, backward-compatible for rolling deployment, observable, resumable, and tested on production-scale samples. |

### 10.11 Test quality targets

- Backend line coverage target: at least 80%; branch coverage at least 75%.
- Critical booking state machine, authorization policy, ledger, payment webhook, refund, OTP, dispute freeze, and payout modules: at least 95% branch coverage plus mutation/property testing where valuable.
- Frontend shared components and critical forms: at least 80% statement/branch target, with accessibility checks.
- End-to-end coverage must include all MVP critical journeys and failure/retry variants.
- Coverage is a release signal, not a substitute for meaningful assertions, risk-based testing, exploratory testing, penetration testing, or load testing.

---

## 11. User journeys

### 11.1 Customer registration and first booking

1. Customer chooses email/password, phone OTP, or Google login.
2. The platform verifies the selected identifier, records consent/terms version, creates a `CUSTOMER` account, and starts a named session.
3. Customer sets name, locale, notification preferences, and optional address.
4. Customer searches a service, grants location or enters an address, and sees serviceability plus eligible providers/services.
5. Customer chooses booking type, time, problem description, address, and optional media.
6. The platform validates inputs and shows a versioned estimate/policy breakdown before confirmation.
7. Booking enters `CREATED` and then `SEARCHING_PROVIDERS`; dispatch begins.

**Success condition:** The customer reaches an offer-comparison screen or a useful no-provider alternative without exposing precise address/contact to unselected providers.

### 11.2 Provider onboarding and approval

1. Provider creates an account and verifies email/phone.
2. Provider completes staged profile, services, skills, radius, schedule, identity, documents, selfie, tax, and payout information.
3. Each file is validated, quarantined/scanned, privately stored, and attached to the application.
4. Provider reviews declarations/consents and submits; status becomes `SUBMITTED` then `UNDER_REVIEW`.
5. Authorized admin reviews only the necessary fields and records decisions/reasons.
6. If more information is required, provider receives a specific request and resubmits the affected item.
7. On approval, provider can configure final availability and go online when all category requirements are valid.

**Success condition:** The approval decision, reviewer, evidence versions, reason, and notification are auditable, and no restricted document becomes public.

### 11.3 Complete instant booking journey

1. Customer confirms an `INSTANT` request.
2. Eligible nearby online providers receive a time-bound real-time request.
3. Providers accept with estimate/ETA or reject.
4. Customer sees comparable active offers and selects one.
5. Other offers expire; booking enters `PROVIDER_SELECTED` then `PAYMENT_PENDING`.
6. Server creates a gateway order with idempotency. Customer pays through hosted/approved gateway UI.
7. Verified webhook/API confirmation causes balanced ledger posting and `PAYMENT_COMPLETED`.
8. Selected provider is assigned; exact route/location access begins under policy.
9. Provider travels; both parties see live status and refreshed ETA.
10. Provider arrives; status becomes `PROVIDER_ARRIVED` then `START_OTP_PENDING`.
11. Customer shares Start OTP in person; provider submits it; server validates it and moves to `IN_PROGRESS`.
12. Provider completes work and uploads required after-service evidence; booking moves to `COMPLETION_PENDING`.
13. Completion OTP is verified; booking moves to `CUSTOMER_CONFIRMATION_PENDING`.
14. Customer confirms satisfaction. Booking becomes `COMPLETED`.
15. After settlement rules and no dispute, held funds are released: commission/tax postings are recorded and provider payable becomes available.
16. Invoice is generated; customer reviews; payout occurs separately; booking becomes `CLOSED` when all closure conditions are met.

**Success condition:** No step can be skipped by manipulating the client, every financial movement balances, and both parties see the same authoritative state.

### 11.4 Scheduled booking

1. Customer chooses a future valid slot in the service zone.
2. System validates lead time, capacity rules, price/policy validity, and schedule.
3. Providers receive scheduled-job offers using configurable advance timing.
4. Customer selects an offer and pays according to configured capture timing.
5. System sends reminders and reconfirms provider availability.
6. If provider cancels or becomes ineligible, controlled redispatch and customer choice occur; price differences require consent.
7. The arrival, OTP, completion, release, and review flow matches instant booking.

**Success condition:** Rescheduling and redispatch preserve history, pricing consent, and refund/fee rules.

### 11.5 Emergency booking

1. Customer chooses only an emergency-eligible service and sees that LocalServe is not a public emergency service.
2. The product displays the emergency fee/premium, fastest-arrival estimate, and applicable cancellation rule before confirmation.
3. Dispatch prioritizes approved emergency-eligible providers who are online, properly equipped, within capped radius, and current-location valid.
4. Customer sees comparable offers sorted by fastest arrival by default and still chooses the provider.
5. Remaining flow uses standard payment, tracking, OTP, completion, evidence, and dispute safeguards.

**Success condition:** Emergency priority does not bypass identity, eligibility, price disclosure, payment verification, or OTP controls.

### 11.6 Cancellation and rescheduling

1. Requesting actor opens cancellation/reschedule flow and receives policy eligibility, fee/refund preview, and downstream impact.
2. Actor selects reason and confirms; server revalidates current status and computes authoritative amounts.
3. One idempotent command changes state, appends history, expires offers/location/chat access as needed, posts refund/fee/compensation entries, and emits events.
4. All affected parties receive consistent notifications and updated timelines.

**Success condition:** Concurrent cancel/select/pay/start commands result in one valid outcome with no double refund or impossible state.

### 11.7 Dispute and financial freeze

1. Eligible customer/provider opens a dispute from the booking, selects category/outcome, and uploads evidence.
2. The system validates the dispute window and atomically moves the booking/payment to dispute/frozen states as applicable.
3. Both parties receive notices, evidence deadlines, and permitted response access.
4. Authorized admin reviews the consolidated case timeline. Every sensitive access is logged.
5. Admin proposes full refund, partial refund, or release with rationale; maker-checker applies where required.
6. Approved resolution creates compensating ledger entries and gateway/refund/transfer actions through idempotent workflows.
7. Parties are notified and may appeal within policy; final closure preserves all history.

**Success condition:** The provider cannot withdraw frozen funds and no admin can alter historical evidence or ledger entries.

### 11.8 Provider withdrawal

1. Provider views ledger-derived available balance and chooses a verified payout destination.
2. Provider submits amount and reauthenticates when policy requires.
3. System checks balance, freezes, risk holds, minimum/maximum, cooling period, destination status, and idempotency.
4. Request is auto-approved or routed for admin approval based on rule/threshold.
5. Payout adapter submits transfer and consumes signed/verified provider callbacks or polling results.
6. Ledger and payout states update through balanced postings; failures release or retain the reserved amount according to reason.

**Success condition:** Retries cannot create duplicate payouts, and the displayed balance reconciles to immutable postings.

### 11.9 Admin provider-verification journey

1. Admin opens prioritized verification queue filtered by SLA/risk/category.
2. The system grants only required masked fields; document view is short-lived and access-logged.
3. Admin approves, rejects, or requests more information per item with reason codes and notes.
4. Final application approval validates that all mandatory items and conflict checks passed.
5. Provider is notified; approval event updates search eligibility asynchronously and idempotently.

**Success condition:** No single low-privilege admin can export identity files or bypass required checks.

### 11.10 Offline/degraded journey

1. Client detects loss of network/socket and shows a persistent offline/reconnecting indicator.
2. Non-idempotent critical actions are not claimed successful without server acknowledgement.
3. Safe drafts/uploads may resume; queued mobile evidence uses explicit status.
4. On reconnect, client refreshes the authoritative booking/payment version, replays only safe idempotent actions, and explains conflicts.

**Success condition:** The user never sees an unverified payment or OTP action as final, and recovery does not duplicate commands.

---

## 12. Booking state-machine policy

### 12.1 Allowed transitions

| From | Allowed to | Authorized initiator/system condition |
|---|---|---|
| `CREATED` | `SEARCHING_PROVIDERS`, `CANCELLED` | System after validation; customer/admin cancellation |
| `SEARCHING_PROVIDERS` | `PROVIDERS_FOUND`, `CANCELLED` | System after first eligible offer; customer/admin/expiry policy |
| `PROVIDERS_FOUND` | `PROVIDER_SELECTED`, `SEARCHING_PROVIDERS`, `CANCELLED` | Customer selects; offers expire and dispatch continues; cancellation |
| `PROVIDER_SELECTED` | `PAYMENT_PENDING`, `CANCELLED` | System opens valid quote/payment window; customer/admin/provider-loss policy |
| `PAYMENT_PENDING` | `PAYMENT_COMPLETED`, `CANCELLED` | Verified server payment result; timeout/cancellation policy |
| `PAYMENT_COMPLETED` | `PROVIDER_ASSIGNED`, `REFUNDED`, `DISPUTED` | Atomic assignment; assignment failure refund; financial exception |
| `PROVIDER_ASSIGNED` | `PROVIDER_ON_THE_WAY`, `CANCELLED`, `DISPUTED` | Assigned provider action; permitted cancellation; issue |
| `PROVIDER_ON_THE_WAY` | `PROVIDER_ARRIVED`, `CANCELLED`, `DISPUTED` | Assigned provider action/geofence-assisted confirmation; policy; issue |
| `PROVIDER_ARRIVED` | `START_OTP_PENDING`, `CANCELLED`, `DISPUTED` | System creates protected Start OTP; permitted no-start cancellation; issue |
| `START_OTP_PENDING` | `IN_PROGRESS`, `CANCELLED`, `DISPUTED` | Valid Start OTP; permitted no-start cancellation; issue |
| `IN_PROGRESS` | `COMPLETION_PENDING`, `DISPUTED` | Assigned provider declares completion; customer/provider issue |
| `COMPLETION_PENDING` | `CUSTOMER_CONFIRMATION_PENDING`, `DISPUTED` | Valid Completion OTP and evidence conditions; issue |
| `CUSTOMER_CONFIRMATION_PENDING` | `COMPLETED`, `DISPUTED` | Customer confirmation/configured safe timeout; dispute |
| `COMPLETED` | `CLOSED`, `DISPUTED` | Closure conditions/dispute window; eligible dispute |
| `DISPUTED` | `COMPLETED`, `REFUNDED` | Approved release/partial-refund resolution; approved full refund |
| `CANCELLED` | `REFUNDED`, `CLOSED` | Payment refund required; no refundable captured payment remains |
| `REFUNDED` | `CLOSED` | Refund and ledger reconciliation complete |
| `CLOSED` | — | Terminal; corrections occur in linked financial/support cases, never by reopening silently |

### 12.2 Transition invariants

1. A command includes `expectedVersion`; a stale version returns `409 CONFLICT` and current state metadata.
2. One transition writes booking state, append-only status history, timeline entry, outbox event, and relevant idempotency record within the same transactional boundary.
3. Only the selected and assigned provider can perform travel, arrival, start, completion, and evidence actions.
4. `PAYMENT_COMPLETED` requires an internally verified `CAPTURED`/`HELD` payment record of the expected amount and currency.
5. `IN_PROGRESS` requires a valid, unused Start OTP issued for the current booking and issuance version.
6. `CUSTOMER_CONFIRMATION_PENDING` requires a valid, unused Completion OTP and any category-required completion evidence.
7. `COMPLETED` does not itself mean provider payout has settled. It makes release evaluation eligible.
8. `CLOSED` requires no open dispute, no incomplete mandatory financial workflow, an invoice outcome, and a consistent status/ledger reconciliation.
9. A full-refund outcome uses `REFUNDED`; a partial refund returns the service lifecycle to `COMPLETED` and records payment `PARTIALLY_REFUNDED` before eventual `CLOSED`.
10. Administrative override is a separate, permissioned domain command with reason, evidence, policy validation, and audit. It cannot directly write any arbitrary status.

### 12.3 Timeout policy

- Offer expiry, dispatch wave, payment window, OTP expiry, arrival inactivity, customer confirmation, dispute response, and settlement hold are configuration values with safe bounds.
- Timeouts enqueue idempotent commands; they do not mutate MongoDB directly from a generic scheduler.
- A delayed job rechecks aggregate version/state before action and becomes a no-op when superseded.
- High-risk, disputed, or fraud-flagged bookings cannot auto-release because of a timeout.

---

## 13. Acceptance criteria

The following cross-functional scenarios are Phase 1 release criteria. Detailed test cases will expand them in Phase 12 without weakening them.

### 13.1 Identity and account acceptance

**AC-IAM-001 — No account enumeration**  
Given an email may or may not exist, when forgot-password is requested, then the public response, status, and observable timing class are equivalent and no existence detail is exposed.

**AC-IAM-002 — OTP attempt protection**  
Given a valid unexpired OTP, when the configured maximum incorrect attempts is exceeded, then the OTP is invalidated/locked, further verification fails, a rate-limit/security event is recorded, and a new OTP is not issued until policy permits.

**AC-IAM-003 — Refresh rotation**  
Given a valid refresh token, when it is used, then a new refresh token is issued and the old token becomes unusable. When the old token is reused, then its token family is revoked and affected sessions require reauthentication.

**AC-IAM-004 — Logout all devices**  
Given a customer has three active sessions, when “logout all devices” succeeds, then all refresh tokens are revoked, protected API calls fail after access-token expiry/revocation policy, and the action appears in login activity.

**AC-IAM-005 — Role isolation**  
Given a valid `CUSTOMER` token, when it calls a provider/admin endpoint or subscribes to another user's booking destination, then the server returns `403`/denies subscription and records no protected data in the response.

**AC-IAM-006 — Suspended provider**  
Given an online approved provider, when an authorized admin suspends the account, then new offer delivery stops promptly, availability becomes ineligible, active bookings enter the configured operations workflow, sessions/actions are restricted, and an audit record is created.

**AC-IAM-007 — Password reset revocation**  
Given active sessions, when password reset completes, then the reset token cannot be reused, all prior refresh sessions are revoked, and a security notice is sent without containing the new password.

### 13.2 Provider verification acceptance

**AC-PRV-001 — Draft resume**  
Given a provider has partially completed onboarding, when the app is closed and reopened on another authorized device, then completed stages and valid uploads resume without duplicate records.

**AC-PRV-002 — No premature discoverability**  
Given provider verification is `DRAFT`, `SUBMITTED`, `UNDER_REVIEW`, `MORE_INFORMATION_REQUIRED`, `REJECTED`, `SUSPENDED`, or `EXPIRED`, when nearby search/dispatch runs, then the provider is excluded.

**AC-PRV-003 — Restricted document access**  
Given an admin without identity-document permission, when a document URL or endpoint is requested, then access is denied. Given an authorized reviewer, access uses a short-lived link and produces a purpose-linked audit event.

**AC-PRV-004 — Rejection clarity**  
Given an item is rejected, when the provider views onboarding, then the affected item, safe reason, correction instructions, appeal/support path, and resubmission eligibility are visible without internal notes.

**AC-PRV-005 — Payout destination change**  
Given a provider changes a verified bank/UPI destination, then reauthentication and verification occur, a configured cooling period/hold applies, both old and new safe destination summaries are notified, and no raw account data appears in logs.

### 13.3 Search and location acceptance

**AC-SRC-001 — Eligibility filtering**  
Given providers at similar distance but one is offline, one has stale location, one lacks the skill, one is suspended, and one is eligible, when nearby search runs, then only the eligible provider is returned.

**AC-SRC-002 — Geospatial correctness**  
Given test providers inside, on, and outside a service radius across a zone boundary, when a GeoJSON search executes, then longitude/latitude order and boundary rules produce the documented candidates.

**AC-SRC-003 — Location privacy**  
Given a booking is still broadcasting, when unselected providers receive the request, then they receive only approximate distance/zone and never exact customer coordinates or contact number.

**AC-SRC-004 — Permission denied fallback**  
Given the customer denies location permission, when searching, then manual address/service-area selection remains usable and the application does not repeatedly block the user with permission prompts.

**AC-SRC-005 — Map degradation**  
Given routing/ETA provider timeout, when the tracking screen loads, then last-known location and freshness are shown where authorized, ETA is marked unavailable/stale, booking actions continue safely, and the failure is observable.

### 13.4 Booking and dispatch acceptance

**AC-BKG-001 — One selected offer**  
Given two concurrent customer selection requests for different offers with the same booking version, when processed, then exactly one succeeds, one receives conflict, and only one provider becomes selected/assigned.

**AC-BKG-002 — Offer expiry**  
Given an expired provider offer, when the customer attempts to select it, then selection is rejected with a refresh action and no payment order is created.

**AC-BKG-003 — Invalid transition**  
Given a booking in `PROVIDER_ON_THE_WAY`, when a client requests direct transition to `COMPLETED`, then the server rejects it, status/history remain unchanged, and the attempt is security/audit observable when suspicious.

**AC-BKG-004 — Stale command conflict**  
Given booking version 11 and a command expecting version 10, when the command is submitted, then it returns a stable `409` error with the latest safe state/version and no duplicate event is emitted.

**AC-BKG-005 — Start OTP binding**  
Given valid Start OTP for booking A, when submitted for booking B or by an unassigned provider, then verification fails, attempts are safely counted, and neither booking enters `IN_PROGRESS`.

**AC-BKG-006 — Separate completion OTP**  
Given the Start OTP was consumed, when it is submitted as Completion OTP, then it fails. A valid current Completion OTP moves only the intended booking to `CUSTOMER_CONFIRMATION_PENDING`.

**AC-BKG-007 — Scheduled provider loss**  
Given a selected provider becomes ineligible before the scheduled service, then the customer is notified, the system follows configured re-selection/redispatch, no replacement is silently accepted, and any price difference requires consent.

**AC-BKG-008 — Cancellation race**  
Given customer cancellation and provider Start OTP arrive concurrently, then database predicates/versioning permit only one policy-valid outcome and fees/refunds are posted once.

**AC-BKG-009 — No-provider outcome**  
Given all dispatch waves end with no active offers, then the customer sees scheduling/radius/category alternatives and may cancel without an incorrect captured charge.

**AC-BKG-010 — Closure prerequisites**  
Given an unresolved dispute, unreconciled mandatory refund, or incomplete invoice job, when closure runs, then booking does not enter `CLOSED` and an owned retry/exception is created.

### 13.5 Payment, ledger, and payout acceptance

**AC-PAY-001 — Frontend success not trusted**  
Given the frontend reports payment success but no valid provider verification exists, when booking is refreshed, then it remains `PAYMENT_PENDING` and provider is not assigned.

**AC-PAY-002 — Webhook verification**  
Given a webhook with invalid signature, altered body, or disallowed timestamp, when received, then it is rejected, no payment/ledger state changes, and a security metric/event is recorded without logging the secret/body fields that are restricted.

**AC-PAY-003 — Webhook replay**  
Given the same valid captured-payment webhook is delivered ten times, then the receipt is deduplicated and exactly one set of balanced postings and one effective booking transition occur.

**AC-PAY-004 — Balanced ledger**  
Given any posted financial transaction, then total debits equal total credits per currency, immutable entries reference one transaction group, and reconciliation can reproduce displayed balances.

**AC-PAY-005 — Dispute freeze race**  
Given payment release and dispute open commands race before payout, then exactly one valid policy order wins; if dispute commits first, releasable/payout balance is frozen and no provider withdrawal can consume it.

**AC-PAY-006 — Partial refund boundary**  
Given a captured amount and prior refunds, when a new partial refund exceeds remaining refundable amount, then the command is rejected. A valid partial refund posts once and preserves the original ledger.

**AC-PAY-007 — Duplicate payout prevention**  
Given a withdrawal request is retried with the same idempotency key and the provider callback is replayed, then at most one external payout is initiated and one final paid posting occurs.

**AC-PAY-008 — Reconciliation exception**  
Given gateway settlement amount does not match expected ledger allocation, then reconciliation creates a uniquely owned exception, prevents unsafe automatic release where configured, and does not “fix” the ledger by editing entries.

**AC-PAY-009 — Promotional wallet restriction**  
Given a customer has promotional credits, when attempting withdrawal, transfer, or conversion to cash, then the action is unavailable/rejected. Credits may only apply to eligible purchases under their rules.

**AC-PAY-010 — Price snapshot**  
Given a commission/tax/catalog rule changes after payment, when completion and release occur, then the booking uses its stored versioned snapshot unless an explicit, consented adjustment workflow exists.

### 13.6 Real-time, chat, and notification acceptance

**AC-RT-001 — Unauthorized subscription**  
Given a valid user guesses another booking destination, when subscribing, then authorization denies it and no event payload is delivered.

**AC-RT-002 — Reconnect recovery**  
Given a client disconnects during three booking updates, when it reconnects, then it refetches the authoritative booking/version and shows correct current state even if historical socket events are not replayed.

**AC-RT-003 — Chat ordering**  
Given messages arrive out of network order, then the conversation uses server sequence/time and idempotent client IDs to display one consistent order without duplicates.

**AC-RT-004 — Typing privacy**  
Given a typing signal, then it expires automatically, is rate-limited, is not stored as durable message/audit content, and is visible only to the authorized booking participant.

**AC-RT-005 — Location freshness**  
Given a location update has an older sequence or implausible timestamp, then it cannot replace a newer accepted point; the anomaly is measured and the customer sees a freshness state.

**AC-NTF-001 — Idempotent notification**  
Given a booking event is redelivered, then each intended channel receives at most one logical notification per template/event/recipient key unless a documented reminder schedule applies.

**AC-NTF-002 — Provider failure**  
Given the push provider fails, then retry/backoff and fallback policy run asynchronously without blocking the booking transaction; final failure reaches an admin-visible queue.

**AC-NTF-003 — Preference enforcement**  
Given promotional push/email consent is off, then campaigns exclude the user. Security/payment notices still use mandatory approved channels.

**AC-CUP-001 — Concurrent usage limit**  
Given a single-use coupon and two concurrent eligible checkout commands, then at most one reservation/consumption succeeds, the campaign counters remain correct, and an unsuccessful/expired payment releases only its own reservation.

**AC-CUP-002 — Campaign budget**  
Given a coupon campaign has insufficient remaining budget for the computed discount, then the checkout does not exceed the budget, returns a clear ineligibility/refresh response, and never changes the customer payable only on the client.

### 13.7 Review, dispute, and admin acceptance

**AC-REV-001 — Verified review eligibility**  
Given a booking is not eligible completed/closed or the requester is not its customer, when review creation is attempted, then it is denied.

**AC-REV-002 — One review per booking**  
Given two concurrent review requests for one booking, then a unique constraint/idempotency allows one review and returns the existing result or conflict for the other.

**AC-REV-003 — Aggregate rebuild**  
Given a review is moderated out, then weighted score/count/distribution update asynchronously and can be reproduced from eligible source reviews.

**AC-DSP-001 — Evidence immutability**  
Given evidence has been submitted, when a party uploads a correction, then a new version is appended and the original checksum/content reference remains preserved under retention policy.

**AC-DSP-002 — Maker-checker**  
Given a proposed resolution exceeds the configured threshold, when the proposing admin attempts approval, then the system rejects self-approval and requires a different authorized admin.

**AC-DSP-003 — Resolution accounting**  
Given full, partial, or release resolution, then the approved amount stays within the frozen/recoverable boundary, ledger entries balance, external action is idempotent, parties receive outcome/reason, and the complete audit chain remains.

**AC-ADM-001 — Admin least privilege**  
Given a content admin, when accessing payment adjustment, provider identity file, role management, or audit export, then access is denied and the attempt is logged according to security policy.

**AC-ADM-002 — Sensitive export**  
Given an authorized export, then fields are minimized/masked, row limit/purpose/expiry/watermark policy applies, access is audited, and the export is private and automatically expires.

**AC-ADM-003 — Feature-flag kill switch**  
Given an enabled risky feature, when an authorized owner disables its kill switch, then new evaluations use the safe default within the defined propagation SLO and the change is audited.

### 13.8 Quality and operations acceptance

**AC-NFR-001 — Load envelope**  
Given the agreed Phase 12 load profile, then core APIs meet Section 10.3 targets without correctness failures, uncontrolled queue growth, or resource exhaustion, and a signed results report records capacity and bottlenecks.

**AC-NFR-002 — Dependency timeout**  
Given map/SMS/payment-read dependency latency exceeds timeout, then bulkheads/circuit breakers prevent thread-pool exhaustion and unrelated booking reads remain within error-budget policy.

**AC-NFR-003 — Backup restoration**  
Given a clean recovery environment, when a scheduled restoration drill is performed, then integrity/reconciliation checks pass and actual RPO/RTO meet Section 10.9 or create release-blocking corrective action.

**AC-NFR-004 — Accessibility**  
Given keyboard-only and supported screen-reader testing, then registration, search, booking, payment return, tracking, dispute, provider offer/OTP, and core admin queues are operable with WCAG 2.2 AA automated and manual evidence.

**AC-NFR-005 — Security gates**  
Given a release candidate, then no unresolved critical vulnerability and no unaccepted high vulnerability exists; secrets, dependency, container, IaC, SAST, authorization, and ZAP tests pass documented thresholds.

**AC-NFR-006 — Observability**  
Given an injected failed payment webhook consumer, Kafka lag, Mongo primary failover, and notification outage in staging, then the correct metrics/alerts/runbooks identify the failure without restricted data leakage.

---

## 14. Assumptions, constraints, and dependencies

### 14.1 Product assumptions

| ID | Assumption | Validation required |
|---|---|---|
| ASM-001 | The first public launch is in selected Indian city zones and uses INR. | Founder/operations approval and zone supply study |
| ASM-002 | Customers are legally able to contract for listed services and providers satisfy applicable local licensing/category requirements. | Legal/category review by city and service |
| ASM-003 | A provider can be an individual or approved business, but each service performer has a traceable identity and assignment. | Provider operations model and terms |
| ASM-004 | The customer pays before standard service start; exceptions require explicit category/payment policy. | Conversion research and payment/legal approval |
| ASM-005 | The payment partner approves LocalServe's marketplace, linked-account, delayed-settlement, refund, reversal, and payout use case. | Written provider onboarding/go-live approval |
| ASM-006 | Masked/offline Aadhaar or alternative identity evidence is sufficient for the initial risk model; raw Aadhaar storage is unnecessary by default. | Legal/verification vendor review |
| ASM-007 | Provider live location is required only while online for dispatch and during an active assigned booking, with transparent controls. | Consent UX research and privacy review |
| ASM-008 | Customers prefer comparing interested providers rather than automatic assignment. | Usability experiment; maintain customer-choice principle unless ADR |
| ASM-009 | Initial search quality can meet launch needs with optimized MongoDB indexes and normalized fields. | Phase 12 relevance/load evidence |
| ASM-010 | A modular monolith reduces initial operational complexity while module contracts make later extraction possible. | Architecture review in Phase 2 |

### 14.2 Constraints

- Core backend must be Java/Spring Boot; Node.js/Express cannot own core business logic.
- MongoDB is the operational source of truth; Redis is not a durable source for money or booking state.
- Kafka events are at-least-once; exactly-once business outcomes come from idempotent consumers and transactional invariants.
- All three roles have separate dashboard navigation, route boundaries, permissions, and experience.
- Identity documents remain private; exact location and contact are progressively disclosed.
- Payment success comes from verified server-side evidence only.
- Public launch is blocked until payment, privacy, identity, consumer terms, tax invoices, provider classification, and grievance processes have formal review.

### 14.3 External dependencies

| Dependency | Primary use | Required fallback/control |
|---|---|---|
| Razorpay | India payment collection, marketplace transfer/settlement where approved, refunds, webhooks | Gateway abstraction, reconciliation, operator queue; Stripe may not be a drop-in for every India flow |
| Stripe | Supported-market collection/refund and future expansion | Capability matrix per country; feature flag; reconciliation |
| Google Maps Platform | Autocomplete, geocode, routing, ETA, maps | Provider abstraction, cached safe results, manual address, stale/unavailable UX |
| Firebase Cloud Messaging | Web/mobile push | In-app inbox, email/SMS fallback for selected critical events |
| Email provider | Verification, security, invoice/transaction notices | Retry, secondary provider readiness, in-app notice |
| SMS provider | OTP and critical transactional notice | Multiple provider adapters, abuse limits, voice/manual support policy where approved |
| S3-compatible storage/KMS | Private documents/evidence/media | Versioning, replication, lifecycle, signed access, restore tests |
| Cloudinary | Approved public media/transforms only | Storage abstraction; no restricted identity evidence |
| Verification vendors | Face match/background/police checks if contracted | Manual verified workflow; never display a check as complete without evidence |

### 14.4 Open decisions for stakeholder approval before Phase 2 freeze

These questions do not block the PRD, but Phase 2 must record approved values or configurable defaults:

1. First launch city/zone and initial service categories.
2. Legal entity, provider contracting model, applicable tax/GST invoicing model, and grievance officer process.
3. Payment partner/product approval and exact capture/transfer/settlement/refund timeline.
4. Standard settlement delay and customer-confirmation timeout by category.
5. Default commission and emergency/cancellation fee boundaries by category.
6. Verification evidence matrix by category and whether any third-party verification vendor is selected.
7. 24/7 emergency/safety support availability; emergency booking must remain disabled outside supported operations windows.
8. Data residency, cloud region, retention schedule, and incident notification workflow approved by counsel/security.
9. Whether customer–provider calling uses number masking in MVP or only in-app chat plus explicit contact disclosure.
10. Initial language set beyond English and launch accessibility test devices/assistive technologies.

---

## 15. Risks and mitigations

Likelihood and impact use `Low`, `Medium`, `High`, and `Critical` for prioritization; owners are organizational roles, not individual names.

| ID | Risk | Likelihood | Impact | Preventive/mitigating controls | Trigger/indicator | Owner |
|---|---|---:|---:|---|---|---|
| RSK-001 | Payment flow is not approved for delayed marketplace settlement or is incorrectly described as escrow | Medium | Critical | Early written payment-partner/legal approval; capability matrix; precise terms; feature-gated release | Partner rejection, settlement mismatch, legal review finding | Finance + Legal |
| RSK-002 | Ledger/gateway inconsistency causes incorrect provider/customer balances | Medium | Critical | Double-entry immutable ledger, idempotency, webhook verification, daily reconciliation, maker-checker, chaos tests | Unbalanced transaction, unreconciled settlement, duplicate payout | Finance Engineering |
| RSK-003 | Aadhaar/PAN/document exposure or misuse | Medium | Critical | Data minimization, masked/offline evidence, private encryption, signed URLs, purpose RBAC, audit, DLP/redaction, retention | Unauthorized access, public URL, sensitive log match | Security + Privacy |
| RSK-004 | Provider fraud, impersonation, or unsafe conduct | High | Critical | Layered verification, selfie/evidence, device/risk signals, category credentials, incident queue, re-verification, suspension | Identity mismatch, repeated safety report, abnormal device sharing | Trust & Safety |
| RSK-005 | Customer/provider physical safety incident | Medium | Critical | Progressive disclosure, booking identity, safety guidance, urgent escalation, location controls, emergency disclaimer, trained operations | Safety ticket, police/legal request, SOS-related report | Trust & Safety + Operations |
| RSK-006 | Insufficient provider density causes long waits and poor fill rate | High | High | Zone-by-zone launch, waitlist, supply heatmaps, category hours, incentives with controls, schedule alternative | Offer rate/first-offer time below target | Provider Operations |
| RSK-007 | Low-quality or inconsistent work causes churn | High | High | Skill/category standards, verified reviews, evidence, repeat quality metrics, training, ranking, remediation | Rating decline, repeat complaints/refunds | Marketplace Operations |
| RSK-008 | Provider economics are unattractive or perceived as opaque | Medium | High | Fee preview, earnings ledger, price control where allowed, fair dispatch, payout SLO, appeals | Provider retention/utilization below target, fee complaints | Product + Provider Ops |
| RSK-009 | Customer/provider collusion, coupon/referral abuse, or off-platform leakage | High | Medium | Abuse limits, device/payment/linkage signals, promotional ledger, contact controls, explainable review queues | Multi-account clusters, abnormal refund/referral patterns | Risk |
| RSK-010 | Live location scale overwhelms database/Kafka/WebSocket infrastructure | Medium | High | Adaptive sampling, Redis presence/latest point, partitioning, aggregation, TTL/retention, backpressure, load tests | Consumer lag, write saturation, socket latency | SRE + Location Engineering |
| RSK-011 | Third-party map, notification, payment, or verification outage blocks core journey | High | High | Timeouts, bulkheads, circuit breakers, queuing, fallback channel/provider, degraded UX, status page/runbooks | Provider error/latency threshold | SRE + Domain Owner |
| RSK-012 | Admin compromise or insider misuse | Medium | Critical | Separate admin app, mandatory 2FA for privileged roles, least privilege, step-up, maker-checker, access reviews, immutable audit | Anomalous export/action, dormant privileged account | Security + Admin Governance |
| RSK-013 | Dispute decisions are inconsistent or biased | Medium | High | Policy matrix, evidence checklist, reason codes, SLA, maker-checker, quality review, appeal, fairness audit | Appeal overturn rate, decision variance | Trust & Safety |
| RSK-014 | Dynamic/emergency pricing harms trust or violates policy | Medium | High | Transparent breakdown, configured caps, opt-in acknowledgment, monitoring, kill switch, no protected-trait targeting | Complaint spike, outlier premium, regulator inquiry | Product + Legal |
| RSK-015 | Worker classification or category licensing creates liability | Medium | Critical | Legal review per market/category, contracts, operating model boundaries, credential rules, insurance decision | Legal notice, policy change, category incident | Legal + Operations |
| RSK-016 | Personal data retention exceeds need or erasure breaks financial/audit obligations | Medium | High | Data inventory, purpose/retention matrix, legal hold, anonymization, deletion orchestrator, verification tests | DSAR failure, orphan data, retention alert | Privacy + Data Engineering |
| RSK-017 | Modular monolith becomes tightly coupled and blocks scaling | Medium | High | Maven/ArchUnit boundaries, domain ownership, contracts/events, no cross-repository access, ADRs | Cyclic dependency, shared schema mutation, coordinated deploy friction | Architecture |
| RSK-018 | Search/ranking unfairly starves new providers or can be manipulated | Medium | High | Cold-start allocation, fair-exposure constraints, versioned features, anomaly detection, outcome/fairness review | Exposure concentration, gaming signals, provider complaints | Search + Marketplace |
| RSK-019 | Support and verification queues exceed operational capacity | High | High | SLA capacity model, staged zone launch, prioritized queues, templates, automation with human decision, workforce plan | Queue age/volume beyond SLA | Operations |
| RSK-020 | Scope size delays launch and reduces quality | High | High | MVP gate, vertical slice delivery, feature flags, risk-first sequencing, explicit V1/Future boundary | Missed milestones, falling test/quality indicators | Product + Engineering |

---

## 16. Success metrics and KPIs

Metrics are cohort- and zone-aware. Targets are initial planning targets for the first 90 days after a zone reaches operational stabilization; they require validation and may be revised through an approved product decision without changing metric definitions.

### 16.1 North-star metric

**Trusted Completed Bookings per Active Service Zone per Week**: unique bookings that reached `COMPLETED`/`CLOSED`, had no substantiated severe safety incident, no duplicate/incorrect financial posting, and remained within the acceptable dispute/refund window.

This combines growth with fulfillment and trust. GTV alone is not the north star.

### 16.2 Marketplace funnel targets

| KPI | Definition | Initial target |
|---|---|---:|
| Search-to-request conversion | Valid booking requests / unique service-result sessions | ≥ 12% |
| Provider offer fill rate | Requests receiving at least one eligible offer within dispatch window / valid requests | ≥ 85% in launched zones |
| Median time to first offer | Request committed to first active provider offer | ≤ 60 s instant; ≤ 30 s emergency |
| Offer-to-selection rate | Requests with selected provider / requests with at least one offer | ≥ 65% |
| Payment success rate | Captured payments / valid payment attempts, excluding explicit user abandonment per definition | ≥ 93% |
| Assigned-to-completed rate | Completed bookings / assigned paid bookings | ≥ 90% |
| Provider no-show rate | Provider no-shows / assigned bookings | < 3% |
| Customer cancellation rate | Customer cancellations / requests with selected provider | < 12%, segmented by stage |
| Median ETA error | Absolute actual-arrival minus shown ETA at assignment | ≤ 10 min for standard urban zones |

### 16.3 Trust and quality targets

| KPI | Initial target/guardrail |
|---|---:|
| Average verified rating | ≥ 4.4/5, monitored with distribution and selection bias |
| Post-booking CSAT | ≥ 4.5/5 among respondents |
| Dispute rate | < 2.0% of completed/eligible bookings |
| Full/partial refund rate | < 3.0%, segmented by cause/category/provider cohort |
| Substantiated severe safety incident rate | 0 target; every incident reviewed |
| Median first support response | ≤ 5 min urgent, ≤ 4 business hours high, ≤ 1 business day normal |
| Dispute resolution within published SLA | ≥ 90% |
| Fake/abusive review confirmed rate | Tracked; no arbitrary target until baseline, with alert threshold |

### 16.4 Retention and provider-health targets

| KPI | Initial target |
|---|---:|
| 90-day customer repeat booking rate | ≥ 30% among eligible first-time customers |
| Monthly active customer retention | Baseline first; improve by cohort without incentive distortion |
| 90-day approved-provider activity retention | ≥ 60% |
| Provider earnings per online hour | Report median and p25/p75 by category/zone; no single misleading average |
| Provider offer response within expiry | ≥ 70% for relevant in-radius offers |
| Provider payout on-time rate | ≥ 99% after funds become eligible and approved |
| Provider support satisfaction | ≥ 4.3/5 among respondents |

### 16.5 Platform and financial integrity targets

| KPI | Target |
|---|---:|
| Core API monthly availability | ≥ 99.95% |
| Payment webhook ingestion availability | ≥ 99.99% |
| Duplicate effective financial postings | 0 |
| Unbalanced ledger transaction groups | 0 |
| Daily reconciliation completed by SLA | 100% |
| Unowned reconciliation exceptions | 0 |
| Critical notification delivery within channel SLO | ≥ 99% excluding confirmed invalid destination/provider-wide outage |
| Crash-free supported web/mobile sessions | ≥ 99.5% |
| Critical/high security findings past approved SLA | 0 |

### 16.6 KPI guardrails

- Conversion improvements are invalid if severe incidents, dispute rate, incorrect charges, accessibility failures, or provider earnings/fairness materially worsen.
- Provider acceptance rate is diagnostic, not a quota; it is evaluated only for relevant offers within provider-declared constraints.
- Rating averages must always be paired with count/distribution and moderation policy.
- Heatmaps and local KPIs require minimum cohort size and location aggregation.
- Admin productivity never justifies bypassing maker-checker, privacy, or evidence requirements.

---

## 17. Product operations and launch policy

### 17.1 Zone-based launch

1. Define service polygons, operating hours, supported categories, provider capacity, emergency coverage, price/fee rules, and support coverage per zone.
2. Onboard and approve minimum viable supply before customer marketing.
3. Run internal and invited-user bookings with real payments in a tightly controlled pilot after gateway approval.
4. Expand demand gradually while monitoring fill rate, first-offer time, cancellations, disputes, support load, payout health, and safety.
5. Pause category/zone acquisition automatically or operationally when guardrails fail; existing bookings remain supported.

### 17.2 Release gates

Public launch requires all of the following:

- Product, architecture, threat model, privacy, legal, tax, and payment-partner reviews complete.
- All critical acceptance journeys pass in production-like staging.
- Reconciliation, refund, dispute freeze, payout, and restore drills pass.
- No unresolved critical/high security finding outside approved time-bound exception.
- SLO dashboards, alerts, on-call, runbooks, incident process, and ownership are active.
- Provider verification and customer/provider support have trained staffing and measurable queue capacity.
- Terms, privacy notice, fee/refund/cancellation policy, grievance route, safety guidance, and accessibility statement are published.
- Feature flags can disable emergency booking, new dispatch, provider onboarding, payouts, and promotional campaigns independently.
- Rollback and forward-fix paths are tested without corrupting events or schemas.

### 17.3 Responsible experimentation

- Experiments need owner, hypothesis, audience, duration, success metric, guardrails, sample plan, consent/privacy review, and stop conditions.
- Authentication security, payment integrity, identity verification, safety escalation, and dispute rights are not weakened for experiments.
- Dynamic price and ranking experiments must retain transparency, caps, fairness review, and auditability.
- Users must not receive conflicting legally material terms within one booking; policy version is snapshotted.

---

## 18. Phase 1 completion record

### Completed deliverables

- Product vision, principles, problem statement, goals, and non-goals.
- Locked advanced final-year delivery profile, integration-mode classifications, scope controls, and academic evaluation outcomes.
- Target users, stakeholders, and seven actionable personas.
- Business model, commission logic, incentives, and unit-economics measures.
- MVP, V1 Growth, and Future scope boundaries.
- 178 traceable functional requirements across identity, customer, provider, catalog/search/location, booking, payment, real time, notifications, reviews, disputes, coupons/referrals/loyalty, admin, analytics, audit, and files.
- Production non-functional requirements for scale, availability, performance, reliability, security, privacy, accessibility, observability, recovery, maintainability, and testing.
- Ten end-to-end user journeys.
- Canonical booking state-transition table and transition invariants.
- 62 cross-functional acceptance scenarios covering success, failure, concurrency, retry, authorization, accessibility, and recovery.
- Assumptions, constraints, external dependencies, open decisions, 20 major risks with mitigations, launch gates, success metrics, and KPIs.

### Important architectural decisions

- India-first, INR-first marketplace; `LocalServe Marketplace` is the provisional brand and `localserve` is the stable code identifier.
- Java 21/Spring Boot modular monolith with enforced domain boundaries and later extraction readiness.
- MongoDB is the operational source of truth; Redis is ephemeral/distributed coordination; Kafka is the durable event backbone.
- Server-authoritative state machine, transactional outbox/inbox, optimistic concurrency, and idempotency guard booking integrity.
- The payment design is platform-held/delayed settlement through an authorized provider, not an unsupported claim of legal escrow.
- Immutable double-entry sub-ledger plus external-provider reconciliation governs all money views and actions.
- Precise location/contact and restricted documents use progressive disclosure, private storage, masking, purpose RBAC, and access audit.
- Customer choice among interested providers remains a core product rule.
- Delivery is constrained to an advanced but explainable final-year capstone: the complete core workflow must run locally, while expensive, regulated, or hyperscale capabilities use honest sandbox, simulated-port, or architecture-ready classifications.

### Project files created

- `docs/LOCAL_SERVE_PRODUCT_SPECIFICATION.md` — central specification and complete Phase 1 PRD.

### Database changes

- None in Phase 1. Canonical entities, ID/money/time conventions, status registers, retention needs, and consistency requirements are defined for Phase 3.

### APIs added

- None in Phase 1. API namespace, versioning, correlation, idempotency, pagination, and compatibility conventions are locked for Phase 4.

### Security controls added

- No runtime controls are implemented in this documentation phase.
- Required controls are specified for authentication, authorization, tokens, OTP, brute-force defense, admin 2FA/step-up, private documents, sensitive-data redaction, webhook verification, immutable audit/ledger, encryption, file validation, vulnerability gates, and incident response.

### Tests added

- No executable tests are added in Phase 1.
- Acceptance scenarios, SLO targets, coverage targets, security gates, restoration drills, load criteria, and traceability requirements define the future test baseline.

### Environment variables required

- None for Phase 1 because there is no runtime application.
- Secrets and configuration variable names will be defined in Phase 2/5; no secret value will be stored in this specification or repository.

### Instructions to run the current phase

Phase 1 is documentation-only. Open this Markdown file in a GitHub-compatible viewer. Review the open decisions in Section 14.4 and record approved choices before or during Phase 2. No database, server, container, or cloud resource is required.

### Remaining work for Phase 2 — System Architecture

Phase 2 must produce:

1. System context, container, component, deployment, trust-boundary, event-flow, booking, payment/ledger, notification, location, and real-time Mermaid diagrams.
2. Backend Maven module map with allowed dependencies and extraction boundaries.
3. Customer/provider/admin frontend and React Native mobile architecture.
4. REST, WebSocket, Kafka, Redis, MongoDB, storage, maps, payments, notifications, and verification integration topology.
5. Authentication/token/session, authorization, data encryption, admin trust zone, secrets, and threat-model architecture.
6. Payment capture, held funds, release, refund, dispute freeze, payout, webhook, reconciliation, and failure/recovery architecture.
7. Horizontal scaling, partitioning, caching, resilience, backpressure, deployment, backup, DR, and multi-region evolution strategy.
8. Observability signals, SLO/error-budget ownership, runbook boundaries, and audit separation.
9. Architecture Decision Records for open choices and a Phase 1 requirement-to-Phase 2 component traceability matrix.

Phase 2 may refine architecture details but must not silently alter the locked vocabulary, status registers, product principles, financial guardrails, or release criteria in this document.

---

## Appendix A — Phase roadmap

| Phase | Primary artifact/outcome |
|---|---|
| 1 | Central product specification and PRD |
| 2 | System architecture and diagrams |
| 3 | MongoDB/Redis/Kafka data design and schemas |
| 4 | REST/webhook/WebSocket API contracts and OpenAPI baseline |
| 5 | Spring Boot backend implementation |
| 6 | Customer/provider/admin web applications and design system |
| 7 | Authentication and authorization hardening/integration |
| 8 | Complete booking/dispatch/OTP engine |
| 9 | Payment, held-funds ledger, refunds, payout, and reconciliation |
| 10 | Real-time chat, presence, tracking, and notifications |
| 11 | Admin application and operational analytics |
| 12 | Full automated, performance, security, recovery, and E2E verification |
| 13 | Container, CI/CD, infrastructure, deployment, SSL, backup, and rollback |
| 14 | Production optimization, high availability, cost, DR, and launch checklist |

## Appendix B — Definition of Phase 1 approval

Phase 1 becomes an approved baseline when the product owner confirms:

- the product identity and India-first scope;
- the MVP/V1/Future boundaries;
- the customer-choice booking model;
- the prepayment and delayed-settlement guardrails;
- the canonical role/status registers;
- the launch metrics and risk ownership; and
- either answers Section 14.4 or authorizes Phase 2 to use documented, configurable defaults.

Approval advances the document to version `1.0.1-approved`; later material changes use ADRs and semantic document versioning.
