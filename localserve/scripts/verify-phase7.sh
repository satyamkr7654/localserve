#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -z "$(type -P mvn || true)" ]]; then
  echo "Maven 3.9.6 or newer is required for Phase 7 verification." >&2
  exit 2
fi

cd "$project_dir/backend"
mvn --batch-mode --no-transfer-progress -pl application -am verify

cd "$project_dir"
./scripts/verify-phase6.sh
