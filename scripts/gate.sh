#!/usr/bin/env bash
# Relentless gate for Parakey. Proves the phone build, not an emulator build.
#
#   bash scripts/gate.sh
#   SERIAL=<adb serial> SKIP_BUILD=1 bash scripts/gate.sh
#
# Exits 0 only when the harness scores at least 99/100, every check passes,
# and the run happened on a physical device with the Moto G85 gate green.

set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

args=(--min-score 99)
[[ -n "${SERIAL:-}" ]] && args+=(--serial "$SERIAL")
[[ "${SKIP_BUILD:-0}" == "1" ]] && args+=(--skip-build)
[[ "${SKIP_INSTALL:-0}" == "1" ]] && args+=(--skip-install)

python3 scripts/verify.py "${args[@]}"
score_status=$?

python3 scripts/gate-guards.py
guard_status=$?

if [[ "$score_status" -eq 0 && "$guard_status" -eq 0 ]]; then
  exit 0
fi
exit 1
