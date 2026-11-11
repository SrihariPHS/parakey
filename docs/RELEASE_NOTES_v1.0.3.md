# Parakey 1.0.3 — themes, number row and long-press gestures

This release keeps the verified typing and voice baseline and adds the feature
round requested after 1.0.1. It also matches the test pad to the AMOLED and
accent palette selected for the keyboard. Every new feature defaults to the previous
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
- Seven weak checks were replaced with seven real feature checks, so the suite
  still totals exactly 100:
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
