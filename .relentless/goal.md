# Parakey relentless goal

**Goal:** a real, system-wide Android keyboard that feels trustworthy for everyday typing and voice dictation on a Moto G85, with private on-device Parakeet speech.

**Bar / metric:** the live 100-check suite in `scripts/verify.py` must score **≥99/100**. The 99 automated checks must pass; the 100th is the physical Moto G85 microphone/insertion gate when the phone is connected. A run on an emulator is capped at 99/100.

**Baseline:** 59/100 at the first live suite run (2026-11-09).
**Current:** 99/100 on emulator + the physical Moto gate passed on the reference phone (2026-11-09).

**Gate command:**

```bash
python3 scripts/verify.py --serial <device> --skip-build
python3 scripts/verify.py --serial <device> --loop 3 --min-score 99
```

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
