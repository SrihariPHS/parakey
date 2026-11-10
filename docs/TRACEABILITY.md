# Requirements traceability

Every user request is mapped to the artifact that proves it. Check IDs come from
`scripts/verify.py` and the latest run in `verification/report.json`.

| # | Request | Implementation | Proof |
| --- | --- | --- | --- |
| 1 | System-wide Android IME, Gboard-style | `ParakeyImeService.kt`, `KeyboardView.kt`, `KeyboardLayout.kt` | I01–I16, K01–K24, R01–R04 |
| 2 | Private on-device Parakeet voice typing | `voice/ParakeetEngine.kt`, `AudioRecorder.kt`, `ModelRepository.kt` | D01–D14, D16–D25, P07–P08, R01, R03 |
| 3 | Accurate typing: dictionary, contractions, spacing, suggestions | `text/SuggestionEngine.kt`, `TextPostProcessor.kt` | K01–K03, K18, K23, K24 |
| 4 | No dead zones / tiled hitboxes | `KeyboardView.layoutGeometry`, touch slop | K18, human profiles K01–K03 |
| 5 | Four theme modes: System, Light, Dark, AMOLED | `KeyboardTheme.palette`, `MainActivity.buildThemeCard` | T01, T02 |
| 6 | Five accent palettes with readable contrast | `KeyboardTheme.accentPalette` | T01 (100 contrast pairs), T02 |
| 7 | Optional number row 1–0, default off | `KeyboardLayouts.specFor(..., numberRow)`, `SettingsStore.numberRowEnabled` | N01, N02, JVM `numberRowIsOptionalAndAddsOneDigitRow` |
| 8 | Long-press accent/punctuation popup | `KeyboardView.alternatesByKey`, `openAlternatePopup`, `commitAlternate` | A01 |
| 9 | Long-press space moves the cursor | `beginCursorMode`, `handleCursorMove`, `ParakeyImeService.onCursorMove` | A02 |
| 10 | Sound and haptic toggles | `KeyboardView.tapFeedback`, `SettingsStore` | S01 |
| 11 | Swipe left on backspace deletes a word | `KeyboardView` backspace gesture, `ParakeyImeService.onDeleteWord` | K08 |
| 12 | Recently used emoji move to the front | `SettingsStore.emojiRecents`, `KeyboardLayouts.emoji` | K17, JVM `recentEmojiMoveToTheFrontWithoutChangingPageSize` |
| 13 | Works on Moto G85 and other Android 8.0+ arm64 phones | `minSdk 26`, arm64/x86_64 ABI split, compatibility doc | B03, B09–B11, R11, `docs/COMPATIBILITY.md` |
| 14 | 100-check live gate, ≥99/100 | `scripts/verify.py`, `scripts/gate.sh`, `gate-guards.py` | latest `verification/REPORT.md` |
| 15 | Signed release on GitHub, no debug hooks | `app/build.gradle.kts`, debug-only manifest | B14, P03, release asset |
| 16 | Phone coordinated with other agents, PIN never stored | session coordination, no PIN in repo | `git grep` for PIN returns nothing; phone released before/after runs |
| 17 | Multiple agents used to audit the work | six read-only auditors; findings folded into this round | audit conclusions summarized in `verification/REPORT.md` |

## Check count

The suite is exactly **100** checks. The feature round added T01, T02, N01, N02,
A01, A02 and S01, and retired seven checks that only re-tested page opens,
duplicate password coverage, or screenshot existence. No feature check can pass
with the feature off: N01 asserts `viewNumberRow`, A01 asserts a live popup and
a committed alternate, A02 asserts the exact caret position, S01 asserts live
counters after real taps, and T01/T02 compare the live view palette with the
requested settings.
