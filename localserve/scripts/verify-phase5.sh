#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"

python3 - <<'PY'
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path('.')
poms = sorted(root.glob('**/pom.xml'))
for pom in poms:
    ET.parse(pom)

java_files = sorted(root.glob('backend/**/src/**/*.java'))
assert java_files, 'no Java source files found'
for source in java_files:
    text = source.read_text(encoding='utf-8')
    assert 'TODO' not in text and 'FIXME' not in text, f'placeholder marker in {source}'
    assert 'System.out.' not in text, f'console logging in {source}'
    match = re.search(r'^package\s+([\w.]+);', text, re.MULTILINE)
    assert match, f'missing package declaration in {source}'
    package_path = Path(*match.group(1).split('.'))
    assert str(source.parent).endswith(str(package_path)), f'package/path mismatch in {source}'

statuses = re.search(r'enum BookingStatus\s*\{([^}]*)}',
    (root / 'backend/booking-dispatch/src/main/java/com/localserve/booking/domain/BookingStatus.java').read_text()).group(1)
actual = [item.strip().strip(',') for item in statuses.splitlines() if item.strip()]
expected = ['CREATED','SEARCHING_PROVIDERS','PROVIDERS_FOUND','PROVIDER_SELECTED','PAYMENT_PENDING',
    'PAYMENT_COMPLETED','PROVIDER_ASSIGNED','PROVIDER_ON_THE_WAY','PROVIDER_ARRIVED','START_OTP_PENDING',
    'IN_PROGRESS','COMPLETION_PENDING','CUSTOMER_CONFIRMATION_PENDING','COMPLETED','DISPUTED',
    'CANCELLED','REFUNDED','CLOSED']
assert actual == expected, f'booking status drift: {actual}'
print(f'static verification passed: {len(poms)} POMs, {len(java_files)} Java files')
PY

if [[ -n "$(type -P mvn || true)" ]]; then
  mvn --batch-mode --no-transfer-progress -pl backend/application -am verify
else
  echo "Maven is unavailable; XML/source invariants passed but Java compilation and tests were not executed." >&2
  exit 2
fi
