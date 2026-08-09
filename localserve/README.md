# LocalServe

LocalServe is an on-demand local-services marketplace designed as a modular monolith with explicit extraction boundaries. The repository currently contains the frozen product, architecture, data and API specifications, the Phase 5 Java backend foundation, the Phase 6 customer/provider/admin web applications and Phase 7 production authentication.

## Repository map

- `docs/LOCAL_SERVE_PRODUCT_SPECIFICATION.md` — central product source of truth.
- `docs/PHASE_2_SYSTEM_ARCHITECTURE.md` — logical, real-time, payment and deployment architecture.
- `docs/PHASE_3_DATABASE_DESIGN.md` — MongoDB, Redis and Kafka contracts.
- `docs/PHASE_4_API_DESIGN.md` — frozen REST, webhook and STOMP contract.
- `docs/PHASE_5_BACKEND_DEVELOPMENT.md` — implemented backend scope and runbook.
- `docs/PHASE_6_FRONTEND_DEVELOPMENT.md` — implemented web applications, design system, security and runbook.
- `docs/PHASE_7_AUTHENTICATION.md` — implemented identity, OAuth, session, admin MFA and route-protection scope.
- `backend/` — Java 21/Spring Boot multi-module backend.
- `frontend/` — Next.js/React/TypeScript workspace with three role applications and five shared packages.
- `infrastructure/` — local container topology and database migrations.

## Verify

Backend requirements: Java 21, Maven 3.9.6 or newer, Docker Compose v2. Frontend requirements: Node.js 24 and npm 11.

```bash
./scripts/verify-phase5.sh
./scripts/verify-phase6.sh
./scripts/verify-phase7.sh
cp .env.example .env
# Replace every secret placeholder and configure an OIDC issuer.
docker compose -f infrastructure/compose/docker-compose.yml up --build
```

Do not use `.env.example` values in a shared or production environment. Production secrets belong in a managed secret store and identity documents belong in encrypted private object storage.
