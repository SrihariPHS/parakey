# Relentless lanes

- **Conductor:** main Parakey thread — contract, gate, ledger, ship.
- **Builder:** main thread worktrees for one mechanism per iteration.
- **Verifier:** independent read-only agents rerun `scripts/verify.py` and audit the diff/harness.
- **Critic:** read-only agents compare the keyboard against Gboard/HeliBoard/FlorisBoard and hunt what the gate misses.
- **Device/release:** Moto G85 physical gate, signed APK, GitHub release.

Only artifacts cross lanes. A run is accepted only from a clean device state and a recorded seed. Never relax the gate to make a score pass.

## Human-like typing gate

`human_type` in `scripts/verify.py` types seeded phrases with jitter, edge/gap taps, drift and adjacent-key slips. Precise is raw (autocorrect off) and must be exact. Average/sloppy use the dictionary and must stay under WER/CER thresholds. A separate hitbox-corner check is raw, and a dictionary check proves both positive and negative autocorrect behavior.
