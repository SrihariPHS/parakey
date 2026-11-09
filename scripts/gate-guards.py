#!/usr/bin/env python3
"""Hard guards on top of scripts/verify.py's own score.

verify.py exits 0 at 99/100 on an emulator because the Moto G85 point is
PENDING, not failed. This makes that run fail instead. It also fails when
any check is not pass, so a 99/100 built from the wrong failing check cannot
count.

Run from the repo root, after verify.py has written verification/report.json.
Prints GUARD lines that a relentless gate reads, and exits 0 only when all
guards are green.
"""

from __future__ import annotations

import json
import pathlib
import sys

REPORT = pathlib.Path("verification/report.json")


def fail(*lines: str) -> int:
    for line in lines:
        print(line)
    sys.exit(1)


def main() -> int:
    if not REPORT.is_file():
        fail("GUARD report present FAILED: verification/report.json is missing")

    try:
        report = json.loads(REPORT.read_text())
    except json.JSONDecodeError as exc:
        fail(f"GUARD report parses FAILED: {exc}")

    checks = report.get("checks") or []
    by_id = {check.get("id"): check for check in checks}
    device = report.get("device") or {}
    emulator = bool(device.get("emulator"))
    physical = by_id.get("R11") or {}
    physical_status = physical.get("status", "missing")

    if emulator:
        fail(
            "GUARD physical device present FAILED: the run scored on an emulator",
            f"GUARD physical gate FAILED: status {physical_status}",
            "GUARD connect the Moto G85 over USB or wireless ADB and rerun",
        )

    print("GUARD physical device present pass")
    print(f"GUARD physical gate {physical_status}")

    failed = [check.get("id", "?") for check in checks if check.get("status") != "pass"]
    if not checks:
        fail("GUARD all checks pass FAILED: the report lists no checks")
    if failed:
        fail("GUARD all checks pass FAILED: " + ", ".join(failed))
    print("GUARD all checks pass")

    if report.get("physical_pending"):
        fail("GUARD physical gate FAILED: report still marks the physical gate pending")

    threshold = report.get("threshold")
    score = report.get("score")
    if isinstance(threshold, int) and isinstance(score, int) and score < threshold:
        fail(f"GUARD score threshold FAILED: {score}/{report.get('total')} below {threshold}")

    print(f"GUARD score threshold pass: {score}/{report.get('total')} (threshold {threshold})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
