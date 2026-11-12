# Parakey 1.1.3 — built-in updates

This release adds a built-in updater and keeps the typing/voice baseline.
Every new feature defaults to the previous behaviour; the number row is off,
sound and haptics are on, the theme is System/mint.

## Updates

- **Updates card** in setup: current version, release notes, a **Check now**
  button and a link to the release history.
- **Daily automatic check** (on by default, can be turned off). Only a successful
  check resets the 24-hour timer, so an offline launch retries next time.
- **Optional automatic download** (off by default). Android always shows its own
  install confirmation; nothing installs silently.
- **Verified downloads:** the APK must come from
  `github.com/SrihariPHS/parakey/releases/download/…`, its SHA-256 must match
  the published release digest, and the file is served to the installer through
  a read-only provider limited to `cache/updates`.
- New permission: `REQUEST_INSTALL_PACKAGES`, the standard Android permission
  for self-updates.

## Clipboard

- **Tap a clip to paste it.** The current clipboard card and every recent row
  are now paste targets, like Gboard.
- The redundant Paste button is gone. Copy, All and Clear remain as small
  secondary actions.
- Recent clips now show up to three rows instead of one, and Android's
  automation labels (`COPY_SELECTION`, `CUT_SELELCTION`) are filtered out.
- Select-all is more reliable: it sets the editor selection directly, and Copy
  keeps a fallback of the selected text if the host drops the selection when
  the panel opens.

## Appearance

- Four theme modes: **System**, **Light**, **Dark** and true-black **AMOLED**.
- Five accent palettes: **mint**, **blue**, **violet**, **sunset** and **rose**.
- The setup screen and the keyboard share the same palette; all 20 combinations
  pass contrast checks.
- Theme and accent survive an IME relaunch.

## Typing

- Optional **number row** with 1–0 above the letters: the original raised-key
  look, 20% shorter than a letter row. Off by default.
- **Number-only pad** for PIN, phone and quantity fields: 3×4 keypad with
  backspace, enter and an ABC escape; `.`/`-` when the field requests them;
  voice, suggestions and long-press symbols disabled.
- **Long-press popups** for accented letters, punctuation and digit symbols.
- **Long-press space** moves the cursor; releasing inserts no space.
- **Swipe left on backspace** deletes the previous word.
- **Emoji recents** keeps the last eight emoji at the front of the emoji page.
- Conservative **two-edit autocorrect**, text shortcuts (`btw`, `idk`, `omw`),
  smart quotes, and guard rails for names and technical terms.
- Independent **sound** and **haptic** toggles.
- Dictionary autocorrect, contractions, double-space period, tiled hitboxes and
  two-thumb typing remain.

## Verification

- 100 checks, all 99 automated checks pass on the emulator; only the physical
  Moto G85 microphone gate is pending when no phone is connected.
- New **U01** calls the live GitHub releases API from the app and requires a
  trusted repository URL, a 64-character SHA-256, a plausible APK size and the
  declared app version.
- New **U02** downloads the real release APK and requires the downloaded file
  to match the published SHA-256.
- The suite still totals exactly 100: U01/U02 replace two duplicate checks.

## Compatibility

Android 8.0+ (API 26), arm64-v8a. The Moto G85 (Android 16) remains the
physical reference device. Update checks use HTTPS; dictation stays offline.
