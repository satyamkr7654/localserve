# LocalServe frontend

Three independently deployable Next.js applications share contracts, UI, security and state infrastructure through npm workspaces.

| Application | Local URL |
|---|---|
| Customer | `http://localhost:3000` |
| Provider | `http://localhost:3001` |
| Admin | `http://localhost:3002` |

## Start

```bash
cp .env.example .env.local
npm ci --ignore-scripts
npm run dev
```

## Verify

```bash
npm run lint
npm run typecheck
npm run test
NEXT_TELEMETRY_DISABLED=1 TURBO_TELEMETRY_DISABLED=1 npm run build -- --concurrency=1
```

The current dashboards use compile-time preview fixtures and do not claim backend mutations succeeded. Authentication integration begins in Phase 7; booking, payments, real time and full admin operations follow the frozen phase plan.
