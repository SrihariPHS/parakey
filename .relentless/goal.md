<!-- relentless:begin -->
metric: verify_score
direction: up
baseline: 95
target: 99
floor_attempts: 3
gate: bash scripts/gate.sh
evidence: verification/report.json,verification/REPORT.md
bar: Gboard voice typing on a Pixel 9, recorded side by side with Parakey on the Moto G85
lanes: 5
<!-- relentless:end -->

# Parakey relentless goal

**Goal:** a real, system-wide Android keyboard that feels trustworthy for everyday typing and voice dictation on a Moto G85, with private on-device Parakeet speech.

**Bar / metric:** the live 100-check suite in `scripts/verify.py` must score **≥99/100**. The 99 automated checks must pass; the 100th is the physical Moto G85 microphone/insertion gate when the phone is connected. A run on an emulator is capped at 99/100.

**Baseline:** 95/100 on the emulator at commit `97b5fec`, recorded 2026-11-09 21:15 UTC. Five checks fail: `K06`, `K07`, `D19`, `R04`, `R11`. Four look like emulator or harness state (`uiautomator dump` returned nothing, `field_message` and `field_email` not found, mic peak 0.00024) and one is the physical gate, which is pending without the phone.

**Earlier high-water mark:** 99/100 on the emulator plus a green physical Moto gate, from the run that produced `verification/artifacts/physical-device.png` at 19:45 local. That report was overwritten by the 21:15 run and was never committed, so it cannot be re-checked from the repo. Treat it as a claim, not a current measurement.

**Gate command:** `bash scripts/gate.sh` (add `SERIAL=<serial>` for a specific device, `SKIP_BUILD=1` and `SKIP_INSTALL=1` to reuse the installed APK).

It runs `scripts/verify.py --min-score 99`, then `scripts/gate-guards.py`. The wrapper exists because `verify.py` exits 0 at 99/100 on an emulator. The guard fails the run unless the device is physical, `R11` passes, no check failed, and the score meets the threshold.

**Guards:**
- APK contains arm64-v8a and x86_64 sherpa-onnx JNI.
- No crashes in the suite (R12).
- Password fields disable voice and clipboard history.
- Model and tokenizer SHA-256 verified on the actual device.
- Human-like typing: precise raw hitbox exactness; average/sloppy WER thresholds; dictionary/autocorrect positive and negative controls.
- Release APK is signed and contains no debug receiver.

**Evidence:**
- `verification/report.json`, `verification/REPORT.md`
- `verification/artifacts/human-typing-*.json`, screenshots
- `.relentless/ledger.tsv`
- GitHub: https://github.com/SrihariPHS/parakey
