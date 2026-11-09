# Security

Parakey processes speech on the device and does not send audio to a server.

## Reporting

Open a private security advisory on GitHub, or contact the maintainer through
the repository. Please do not paste real voice recordings or passwords into an
issue.

## Release vs debug builds

- **Release builds** (the APK on the Releases page) do not contain the debug
  receiver or `SelfTestActivity`. `TryActivity` is not exported.
- **Debug builds** add a `DebugReceiver` used by `scripts/verify.py` to drive the
  keyboard and a transparent self-test activity. Do not distribute debug APKs.
  The harness is intended for a test phone that is not carrying personal data.
- The speech model is stored in the app's private external files directory and
  is SHA-256 checked before it is loaded.
- Clipboard history is app-local, capped at five entries, and is not written
  while a password field is focused. Copy is blocked in password fields.
- `android:allowBackup="false"` keeps keyboard/clipboard preferences off device
  backups.
