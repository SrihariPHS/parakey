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
- **Dictionary**: seeded misspellings (including contractions such as
  `mightve → might've` and `liek → like`), valid words/emails/URLs that must be
  left alone, the UI flow `teh ` → `the ` and `mightve ` → `might've `,
  double-space → period, and a negative control with autocorrect disabled.
- **Suggestions**: type `hel`, confirm relevant prefix suggestions, tap a chip
  off-center, and require the committed text to match the chip plus one space.
- **Hitbox tiling**: key gaps and side margins resolve to the intended key rather
  than dead strips.
- **Themes**: all 20 mode/accent combinations are read from the live IME state
  and checked against WCAG contrast targets for body text, secondary text,
  accent labels, suggestion text and accent-on-background. Theme and accent are
  then persisted across a full IME relaunch.
- **Backspace gestures**: a 400 ms hold starts repeat deletion; a left swipe
  deletes the whole previous word in one gesture.
- **Emoji recents**: inserting 🚀 must add it to the persisted recents list and
  put it in the first emoji cell on the next visit.
- **Number strip**: with the setting on, the keymap must gain exactly ten
  digits and one row, stay inside the view, stay under 1500 px, gain less than
  170 px of height (the strip is 62% of a row), flag every digit as part of the
  strip, and produce `1234567890` from real corner and center taps. Turning it
  off restores the four-row layout.
- **PIN number pad**: launching a real `TYPE_CLASS_NUMBER` PIN field must
  switch the IME to a 3×4 `NUMBER` page with no letters, backspace and enter,
  and four real taps must produce `1234`. The touch path is real; the harness
  does not inject digits through a debug method.
- **Long-press popups**: a real 650 ms hold on `e` opens the popup; sliding to
  the third cell and releasing inserts exactly `ê`; releasing outside cancels
  without inserting the base key.
- **Cursor gesture**: holding space enters cursor mode, sliding left three
  steps moves the caret, and the following `q` lands in the middle of `abcd`
  (`aqbcd`) with no inserted space.
- **Sound and haptics**: counters must stay at zero with both toggles off and
  become non-zero after a real key tap with both on.

Run the same harness on a phone:

```bash
python3 scripts/verify.py --serial <adb-serial> --loop 3 --min-score 99
```

The emulator ceiling is 99/100 because the physical Moto G85 microphone gate is
pending. With the Moto connected, the same suite includes the speaker/mic,
Parakeet ASR and text-insertion gate for 100/100.
