# Parakey 1.0.1

Typing-focused update for the Gboard-style Android keyboard with on-device
Parakeet speech-to-text.

## Typing

- 43k-word AOSP LatinIME English dictionary, bundled offline.
- Prefix suggestions, next-word bigrams and add-to-personal-dictionary chips.
- Contraction and spelling autocorrect: `mightve → might've`, `liek → like`,
  `ive → I've`, `im → I'm`, `thats → that's`, `arent → aren't`, and more.
- Double-space inserts a period, with no double spaces around punctuation.
- Autocorrect is blocked in email/URL/password fields and can be toggled off.
- Personal dictionary and typing settings card.
- Tiled key hitboxes, release-key resolution, touch slop, per-pointer presses
  and a fixed caps-lock gesture.

## Verification

The repo contains a 100-check live suite. The automated emulator report is
99/100 (the missing point is the physical phone gate). The physical Moto G85
speaker/mic gate was exercised on the reference phone. Run the suite against
your own Android device:

```bash
python3 scripts/verify.py --serial <adb-serial> --loop 3 --min-score 99
```

## Compatibility

Android 8.0+ (API 26), arm64-v8a. Moto G85 (Android 16) is the reference phone;
other modern Android phones should work. See `docs/COMPATIBILITY.md`.

The speech model is downloaded once from Hugging Face and SHA-256 verified. It
is CC-BY-4.0 by NVIDIA. App code is Apache-2.0.
