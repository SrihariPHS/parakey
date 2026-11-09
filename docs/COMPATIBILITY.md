# Compatibility

## Tested

- **Moto G85 5G**, Android 16, arm64-v8a, 1080x2400 — reference device.
- Android 15 arm64 emulator (x86_64/arm64 system image) — automated checks.

## Expected to work

- Android 8.0 (API 26) or newer.
- arm64-v8a CPU (nearly all Android phones since 2018). The APK also ships an
  x86_64 native library for emulators.
- A working microphone and permission to use it.
- About 126 MB free storage for the model, plus roughly 300 MB of free RAM
  while the model is loaded.
- Any Android IME host (Messages, Gmail, Chrome, notes, etc.). No root, Google
  account, Play Services, or network connection after the model download.

The app is a standard Android `InputMethodService`. It does not depend on the
Moto G85 hardware or Motorola software, so other phones with the same Android
IME APIs should behave the same.

## Caveats

- Some OEM skins restrict sideloaded keyboards or require an extra "allow
  restricted settings" confirmation on Android 13+. This is an Android security
  prompt, not a Parakey permission.
- Very low-RAM devices (under ~2 GB) may evict the 126 MB model; the first
  dictation after an eviction is slower while it reloads.
- The physical microphone gate in `scripts/verify.py` plays a known WAV through
  the speaker and expects the Moto G85 reference model string. The 99 automated
  checks run on any device; the final point is the Moto G85 acceptance gate.
- The model is English (Parakeet TDT-CTC 110M English). Other languages are not
  covered by this model.
- The keyboard UI is English QWERTY. Android system language and RTL support
  are not localized yet.
