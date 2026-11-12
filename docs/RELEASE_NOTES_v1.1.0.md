# Parakey 1.1.3 — number pad, smarter typing and zoom-free number row

This release keeps the verified typing and voice baseline and adds a functional
round: a dedicated numeric keypad, a slimmer and clearly distinct number strip,
smarter autocorrect and next-word prediction, smart quotes, and a test pad that
follows the selected palette. Every new feature defaults to the previous
behaviour: System theme, mint accent, number row off, sound and haptics on.

## Appearance

- Four theme modes: **System**, **Light**, **Dark** and true-black **AMOLED**.
- Five accent palettes: **mint**, **blue**, **violet**, **sunset** and **rose**.
- The setup screen and the keyboard share the same palette.
- All 20 combinations are checked for WCAG contrast on body text, secondary
  text, accent labels, suggestion text and accent-on-background.
- Theme and accent survive an IME relaunch.

## Typing

- Optional **number row** with 1–0 above the letters. Off by default; the
  symbols and emoji pages are unchanged. The extra row is included in the
  keyboard height and hitbox tiling.
- **Long-press popups** for accented letters, punctuation and digit symbols.
  Hold, slide to the character, release. Releasing outside cancels.
- **Long-press space** enters cursor mode. Slide left or right to move the
  caret one character per 16 dp; releasing inserts no space.
- **Swipe left on backspace** deletes the previous word in one gesture.
- **Emoji recents** keeps the last eight emoji you used at the front of the
  emoji page.
- Independent **sound** and **haptic** toggles.
- Existing dictionary autocorrect, contractions, suggestions, double-space
  period, tiled hitboxes and two-thumb typing are unchanged.

## Fixes in 1.0.3

- The test pad now follows the AMOLED theme and accent palette, including its
  status pills and field colours.
- Long-press gestures survive normal thumb drift instead of being cancelled by
  8 dp of movement.
- Landscape uses a shorter keyboard so the bottom row stays on screen.
- Backspace word-delete clears a selection safely and never corrupts a
  selection-aware edit.
- Emoji recents also record BMP emoji such as ❤️, ✨ and ☕.
- Sound/haptic counters now reflect real system feedback instead of attempts.
- The system dark/light change re-applies the palette immediately.

## Verification

- 100 checks: the previous 99 automated checks plus the physical Moto G85 gate.
- v1.0.2 replaced seven weak checks with seven real feature checks. v1.1.0
  added N03 for the number pad and retired K13 as redundant with N02. The
  suite still totals exactly 100:
  - T01 all 20 palettes meet contrast targets
  - T02 theme/accent survive relaunch
  - N01 number row adds one fitted row and stays under 1500 px
  - N02 digits 1–0 insert from corner and centre taps
  - A01 long-press popup opens, selects and cancels
  - A02 long-press space moves the cursor without inserting a space
  - S01 sound and haptics obey their toggles
- The signed APK is built from the release variant; the debug receiver and
  self-test hooks are not present in the release.
- This report is a full (non-skip-build) run: Gradle build, JVM tests and lint
  actually executed.

## Compatibility

Android 8.0+ (API 26), arm64-v8a. The Moto G85 (Android 16) remains the physical
reference device. No new permissions, dependencies or network calls.

## 1.1.3 additions

- **Number-only pad** for `TYPE_CLASS_NUMBER` and `TYPE_CLASS_PHONE` fields:
  a large 3×4 keypad with backspace, enter and an ABC escape, decimal and minus
  keys when the field asks for them, no letters, no word suggestions, no
  long-press symbols, and voice typing disabled. Toggle it off to keep the
  letter keyboard in number fields.
- **Number row:** restored to the original per-key raised style you preferred,
  now 20% shorter than a letter row (0.8× height). No banded strip.
- **Typing improvements:** a second, conservative two-edit autocorrect pass for
  longer words, more common misspellings, text-expansion shortcuts
  (`btw`, `idk`, `omw`, `lmk`, `tbh`, `brb`, `gtg`), and a much larger next-word
  table.
- **Smart punctuation:** a double quote becomes “ or ” based on context.
- **Guard rails:** two-edit correction never rewrites capitalised names, and a
  protected list covers common technical terms (`params`, `nginx`, `retries`,
  `Priya`). Long-press is not offered on the numeric pad.
- The number strip and number pad default to on; the strip is off. Verified by N01, N02, N03 and K24.

## 1.1.3 — built-in updates

- **Updates card** in setup: current version, release notes, *Check now* and a
  link to the release history.
- **Daily automatic check** (on by default, can be turned off). Only a
  successful check resets the 24-hour timer, so an offline launch retries next
  time.
- **Optional automatic download** (off by default). Android always shows its
  own install confirmation.
- **Verified downloads:** the APK must come from
  `github.com/SrihariPHS/parakey/releases/download/…`, its SHA-256 must match
  the published release digest, and the file is served to the installer through
  a read-only provider limited to `cache/updates`.
- New permission: `REQUEST_INSTALL_PACKAGES` (the standard Android permission
  for self-updates). Nothing installs silently.
- U01/U02 in the 100-check suite now exercise the live release lookup and a real
  verified APK download.
