#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
frontend_dir="$project_dir/frontend"
cd "$frontend_dir"

node_major="$(node -p 'process.versions.node.split(".")[0]')"
if (( node_major < 24 )); then
  echo "Node.js 24 or newer is required; found $(node --version)." >&2
  exit 1
fi

if [[ ! -d node_modules ]]; then
  npm ci --ignore-scripts
fi

export NEXT_TELEMETRY_DISABLED=1
export TURBO_TELEMETRY_DISABLED=1

npm run lint
npm run typecheck
npm run test
npm run build -- --concurrency=1

if [[ "${RUN_E2E:-false}" == "true" ]]; then
  npm run test:e2e
else
  echo "Playwright execution skipped. Set RUN_E2E=true after installing a Chromium browser."
fi
