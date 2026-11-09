# Typing-accuracy verification

The 100-check suite is not a set of perfect center taps. The keyboard checks
include human-like, seeded typing profiles driven through `adb` against the
real IME window and the live key rectangles.

## Profiles

| Profile | Jitter | Edge taps | Gap taps | Adjacent-key slips | Autocorrect |
| --- | --- | --- | --- | --- | --- |
| Precise | 8% of key size | 2% | 0.5% | 0.2% | off (raw hitbox gate) |
| Average | 22% | 8% | 3% | 2% | on |
| Sloppy | 34% | 16% | 6% | 6% | on |

Each profile types a phrase with seeded random jitter, a per-key random walk,
edge points, gap midpoints and occasional neighbouring-key slips. The observed
text is compared to the intended phrase with character error rate (CER) and word
error rate (WER). Phrase and plan details, including every tap coordinate, are
written to `verification/artifacts/human-typing-*.json`.

The suite also checks:

- **Raw hitbox corners**: every corner and the center of six keys, with
  autocorrect and suggestions disabled, must produce exactly the intended
  character. This prevents autocorrect from hiding a broken hitbox.
- **Dictionary**: sixteen seeded misspellings (including contractions such as
  `mightve → might've` and `liek → like`), twelve valid words/emails/URLs that
  must be left alone, the UI flow `teh ` → `the ` and `mightve ` →
  `might've `, double-space → period, and a negative control with autocorrect
  disabled.
- **Suggestions**: type `hel`, confirm relevant prefix suggestions, tap a chip
  off-center, and require the committed text to match the chip plus one space.
- **Hitbox tiling**: key gaps and side margins resolve to the intended key rather
  than dead strips.

Run the same harness on a phone:

```bash
python3 scripts/verify.py --serial <adb-serial> --loop 3 --min-score 99
```

The emulator ceiling is 99/100 because the physical Moto G85 microphone gate is
pending. With the Moto connected, the same suite includes the speaker/mic,
Parakeet ASR and text-insertion gate for 100/100.
