# Requirements traceability

Every user request is mapped to the artifact that proves it. Check IDs come from
`scripts/verify.py` and the latest run in `verification/report.json`.

| # | Request | Implementation | Proof |
| --- | --- | --- | --- |
| 1 | System-wide Android IME, Gboard-style | `ParakeyImeService.kt`, `KeyboardView.kt`, `KeyboardLayout.kt` | I01–I16, K01–K24, R01–R04; I07 duplicates I06 |
| 2 | Private on-device Parakeet voice typing | `voice/ParakeetEngine.kt`, `AudioRecorder.kt`, `ModelRepository.kt` | D01–D14, D16–D25, P07–P08, R01, R03; D23 now inserts mid-text, D24 checks the leading space |
| 3 | Accurate typing: dictionary, contractions, spacing, suggestions, human-like fallback | `text/SuggestionEngine.kt` (two-edit pass, expansions, larger bigrams), `TextPostProcessor.kt` | K01–K03, K18, K23, K24, JVM `SuggestionEngineTest` |
| 4 | No dead zones / tiled hitboxes | `KeyboardView.layoutGeometry` midpoint tiling; 8 dp slop only for top-bar mic/gear/clip | K18, human profiles K01–K03 |
| 5 | Four theme modes: System, Light, Dark, AMOLED | `KeyboardTheme.palette`, `MainActivity.buildThemeCard`, `TryActivity` | T01, T02 (force-stop + relaunch); setup theme card is visually reviewed, not unit-tested |
| 6 | Five accent palettes with readable contrast | `KeyboardTheme.accentPalette` | T01 (100 contrast pairs), T02 |
| 7 | Optional number strip 1–0, default off, visually distinct | `KeyboardLayouts.specFor(..., numberRow)`, `KeyRow.heightFactor`, `KeyboardView` flat strip | N01 (slim delta + flags), N02, JVM `numberRowIsOptionalAndAddsOneDigitRow` |
| 7b | Number-only pad for PIN/phone/decimal fields | `KeyboardPage.NUMBER`, `KeyboardLayouts.numberPad`, `SettingsStore.numericPadEnabled`, `ParakeyImeService.onStartInput` | N03 (real PIN field, real taps, mic/suggestions off), JVM number-pad tests |
| 8 | Long-press accent/punctuation popup | `KeyboardView.alternatesByKey`, `openAlternatePopup`, `commitAlternate` | A01 |
| 9 | Long-press space moves the cursor | `beginCursorMode`, `handleCursorMove`, `ParakeyImeService.onCursorMove` | A02 |
| 10 | Sound and haptic toggles | `KeyboardView.tapFeedback`, `SettingsStore` | S01 |
| 11 | Swipe left on backspace deletes a word | `KeyboardView` backspace gesture, `ParakeyImeService.onDeleteWord` | K08 |
| 12 | Recently used emoji move to the front | `SettingsStore.emojiRecents`, `KeyboardLayouts.emoji` | K17, JVM `recentEmojiMoveToTheFrontWithoutChangingPageSize` |
| 13 | Works on Moto G85 and other Android 8.0+ arm64 phones | `minSdk 26`; one APK with `abiFilters` arm64-v8a + x86_64; compatibility doc | B03, B09–B11, `docs/COMPATIBILITY.md`; **API 26 and other phones are unexercised**; Moto R11 is **UNPROVEN in the current report** |
| 14 | 100-check live gate, ≥99/100 | `scripts/verify.py`, `scripts/gate.sh`, `gate-guards.py` | latest `verification/REPORT.md`: 99/100, all 99 automated checks pass, R11 pending |
| 15 | Signed release on GitHub, no debug hooks | `app/build.gradle.kts`, debug-only manifest | v1.1.1 asset SHA-256 `f1ef2532cdd9ba880762848865763bc0351f8101a1c56558c1ddf67d4833bbc4`; apksigner CN=Parakey; no DebugReceiver/SelfTestActivity; B14/P03 |
| 16 | Phone coordinated with other agents, PIN never stored | session coordination; PIN never written to a file | no PIN value in tracked files or history; thread messages are session evidence, not repo evidence |
| 17 | Multiple agents used to audit the work | two early audits plus five feature-design agents; three deepseek-v4.1-flash auditors completed (traceability, release artifact, feature completeness) | audit findings are folded into the fixes below; the audit transcripts themselves are session evidence, not committed files |

## Check count

The suite is exactly **100** checks. The feature round added T01, T02, N01, N02,
A01, A02 and S01, and retired seven checks that only re-tested page opens,
duplicate password coverage, or screenshot existence. No feature check can pass
with the feature off: N01 asserts `viewNumberRow`, A01 asserts a live popup and
a committed alternate, A02 asserts the exact caret position, S01 asserts live
counters after real taps, and T01/T02 compare the live view palette with the
requested settings.

## Honest gaps

- **R11 physical Moto gate is pending in this run.** It passed in an earlier live Moto run, but that evidence was overwritten and is not in the current report. Reconnect the Moto G85 and rerun `python3 scripts/verify.py --serial <serial>` for a true 100/100.
- **Other phones are supported by API level, not individually tested.** The only physical reference device is the Moto G85; API 26 is not exercised.
- **TalkBack is not implemented.** The keyboard is one canvas view with a single content description and no ExploreByTouchHelper virtual nodes, so key-by-key screen-reader navigation is missing.
- **The release is a single APK with two ABIs**, not an ABI split APK.
- **A skipped-build run does not execute Gradle.** `--skip-build` reports B04–B06 as pass without running them; the committed final report is from a full run, not `--skip-build`.
- **Numeric pad coverage:** PIN and `TYPE_CLASS_NUMBER` fields are covered by N03. Phone and decimal/signed number fields have code paths (including ABC escape, `.` and `-`) but are not individually automated.
- **TalkBack remains unimplemented**; the keyboard is one canvas with one content description.
