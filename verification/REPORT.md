# Parakey verification report

**Score: 99/100** (99/100 checks, threshold 99)

- Generated: 2026-11-12T06:28:59+00:00
- Device: sdk_gphone64_arm64 · Android 15 · arm64-v8a (emulator)
- Serial: `redacted`
- APK: `dcb95112ca519c46…`
- Model: `9177a9146cf32ee0…`
- Duration: 492s

> The physical Moto G85 gate is pending. This run scores 99/100 on the
> emulator. Connect the phone and rerun to reach 100/100.

| ID | Category | Check | Status | Evidence |
| --- | --- | --- | --- | --- |
| B01 | Build | Gradle wrapper is present | PASS | gradlew + wrapper jar present |
| B02 | Build | Project structure is present | PASS | missing=[] |
| B03 | Build | SDK levels are pinned (35/35/26) | PASS | compileSdk=35 targetSdk=35 minSdk=26 |
| B04 | Build | Debug APK compiles | PASS | gradlew :app:assembleDebug |
| B05 | Build | JVM unit tests pass | PASS | gradlew :app:testDebugUnitTest |
| B06 | Build | Android lint passes | PASS | gradlew :app:lintDebug |
| B07 | Build | Debug APK exists | PASS | /Users/srihari/workspace/parakey/app/build/outputs/apk/debug/app-debug.apk (28660074 bytes) |
| B08 | Build | APK is under 40 MB (model not bundled) | PASS | 27.3 MB |
| B09 | Build | APK contains arm64-v8a sherpa JNI | PASS | arm64-v8a sherpa JNI |
| B10 | Build | APK contains x86_64 sherpa JNI | PASS | x86_64 sherpa JNI |
| B11 | Build | APK contains ONNX Runtime | PASS | onnxruntime native library |
| B12 | Build | IME service and method metadata are packaged | PASS | IME service + method metadata + BIND_INPUT_METHOD |
| B13 | Build | Microphone and internet permissions are packaged | PASS | RECORD_AUDIO + INTERNET |
| B14 | Build | Package id and version are correct | PASS | package com.parakey.keyboard version 1.1.3 |
| B15 | Build | Model weights are not committed | PASS | weight files in repo=0, ignored=True |
| B16 | Build | License and model attribution exist | PASS | LICENSE + Parakeet CC-BY-4.0 notice |
| B17 | Build | README documents setup and verification | PASS | README covers installation, Moto G85 and verification |
| P01 | Privacy | No broad storage or overlay permissions | PASS | forbidden=[] |
| P02 | Privacy | IME is protected by BIND_INPUT_METHOD | PASS | IME is bound by the system permission |
| P03 | Privacy | Debug receiver is debug-only | PASS | debug receiver is debug-only |
| P04 | Privacy | Password fields disable dictation | PASS | micEnabled=False status='Voice typing is off in password fields' |
| P05 | Privacy | No cleartext traffic configured | PASS | no cleartext traffic configured |
| P06 | Privacy | No hardcoded API keys or tokens | PASS | matches=[] |
| P07 | Privacy | Model URL is HTTPS | PASS | MODEL_URL=https://huggingface.co/VocaHQ/sherpa-onnx-nemo-parakeet-tdt-ctc-110m-en-int8/res |
| P08 | Privacy | Dictation path makes no network calls | PASS | network references in dictation path=[] |
| I01 | IME | ADB device is online | PASS | state=device model=sdk_gphone64_arm64 |
| I02 | IME | Debug APK is installed | PASS | package:/data/app/~~VwRUYPz2U38OgXOTjNSXYA==/com.parakey.keyboard-gOWyfZJkOkji3DF2RI8yvg==/base.apk |
| I03 | IME | Setup activity launches | PASS | topResumedActivity=ActivityRecord{ace5c27 u0 com.parakey.keyboard/.MainActivity t1328} |
| I04 | IME | Setup header renders | PASS | setup header renders |
| I05 | IME | Speech model is ready on device | PASS | modelReady=True |
| I06 | IME | Parakey IME is listed | PASS | Parakey IME is listed |
| I08 | IME | Parakey IME is selected | PASS | default_input_method=com.parakey.keyboard/.ParakeyImeService |
| I09 | IME | IME service is running | PASS | service state={'voiceMode': False, 'recording': False, 'processing': False, 'clipboardMode': False, 'suggestions': True, 'micEnabled': True, 'page': 'LETTERS', 'shift': 'ON', 'width': 1080, 'height': 819, 'bottomInset': 63, 'numberRow': False, 'alternatePopup': False, 'alternateSelected': -1, 'curso |
| I10 | IME | Test pad launches | PASS | topResumedActivity=ActivityRecord{ae9e47a u0 com.parakey.keyboard/.TryActivity t1329} |
| I11 | IME | Message field is focused | PASS | focused=true |
| I12 | IME | Software keyboard is shown | PASS | shown=True |
| I13 | IME | Keyboard height is sane | PASS | keyboard height=819px |
| I14 | IME | Navigation-bar inset is handled | PASS | bottomInset=63 imeTop=1581 |
| I15 | IME | IME is not fullscreen | PASS | mInFullscreenMode=False |
| I16 | IME | IME survives app switching | PASS | keyboard returns after home/app switch |
| K01 | Keyboard | Precise human typing is character-exact | PASS | CER=0.053 WER=0.000 observed='the quick brown fox ' |
| K02 | Keyboard | Average human typing with autocorrect | PASS | CER=0.043 WER=0.000 observed='please call me tomorrow ' |
| K03 | Keyboard | Sloppy human typing with autocorrect | PASS | CER=0.045 WER=0.000 observed='this is a test message ' |
| K04 | Keyboard | Space key inserts a space | PASS | text='q ' |
| K05 | Keyboard | Period key inserts a period | PASS | text='q.' |
| K06 | Keyboard | Comma key inserts a comma | PASS | text='q,' |
| K07 | Keyboard | Backspace deletes a character | PASS | text='qw' |
| K08 | Keyboard | Long-press backspace repeats | PASS | after long-press text='' after swipe='hello ' |
| K09 | Keyboard | Enter inserts a newline | PASS | text='q\n' |
| K10 | Keyboard | Shift toggles case | PASS | text='qW' |
| K11 | Keyboard | Double-shift caps lock | PASS | shift=CAPS text='AB' |
| K14 | Keyboard | ABC returns to letters | PASS | text='q' |
| K17 | Keyboard | Emoji inserts and appears in recents | PASS | text='🚀' recents=['🚀'] firstEmoji='🚀' |
| K18 | Keyboard | Key-gap taps hit the nearest key | PASS | expected='qqqqqpppppaaaaalllllzzzzzmmmmm' observed='qqqqqpppppaaaaalllllzzzzzmmmmm' |
| K22 | Keyboard | Landscape rotation keeps typing alive | PASS | landscape=True keyboard=2400x604 screen=1080x2400 |
| K23 | Keyboard | Typing suggestions appear and insert | PASS | suggestions=['he', 'held', 'help'] text='he ' |
| K24 | Keyboard | Dictionary autocorrect fires and can be disabled | PASS | typos=15/15 valid=11/11 on='the ' contraction="might've " twoEdit='necessary ' doubleSpace='q. ' off='teh ' smartQuote='“' counter=0->1 failures=[] |
| N03 | Number row | PIN fields get a 3x4 numeric pad | PASS | page=NUMBER numericOnly=True mic=False suggestions=False labels=['1', '2', '3', '4', '5', '6', '7', '8', '9'] text='1234' letters=False |
| T01 | Themes | All 20 theme/accent palettes meet contrast targets | PASS | 100 contrast pairs checked; failures=[] |
| T02 | Themes | Theme and accent survive an IME relaunch | PASS | theme=amoled accent=rose bg=#000000 accentHex=#FB7185 |
| N01 | Number row | Number row is 20% shorter and still fits | PASS | keys 34->44 digits=10 height=819->943 delta=124 rows=5 bad=0 |
| N02 | Number row | Digits 1-0 insert from corner and center taps | PASS | expected='1234567890' observed='1234567890' |
| A01 | Gestures | Long-press popup opens, selects and cancels | PASS | popup=True selected=2 text='ê' base='e' cancelled='' |
| A02 | Gestures | Long-press space moves the cursor without a space | PASS | cursorMode=True before='abcd' after='aqbcd' step=126px |
| S01 | Feedback | Sound and haptics obey their toggles | PASS | off=0/0 on=1/1 |
| U01 | Updates | Update channel finds the latest signed release | PASS | current=1.1.3 latest=1.1.2 trusted=True sha=4410218ccf3b… size=25968337 |
| U02 | Updates | Update APK downloads and verifies its SHA-256 | PASS | version=1.1.2 sha=4410218ccf3b… bytes=25968337 message=downloaded |
| D01 | ASR | Model file is present at the expected size | PASS | model bytes=131652171 expected=131652171 |
| D02 | ASR | Tokenizer is present at the expected size | PASS | tokens bytes=9953 expected=9953 |
| D03 | ASR | Model checksum matches | PASS | model=9177a9146cf32ee0… tokens=450e56bd2f03… path=/data/user/0/com.parakey.keyboard/files/models/parakeet-110m/model.int8.onnx |
| D04 | ASR | Parakeet engine loads | PASS | text='The quick brown fox jumps over the lazy dog' |
| D05 | ASR | Fixture q1 transcription | PASS | WER=0.000 text='The quick brown fox jumps over the lazy dog' |
| D06 | ASR | Fixture q2 transcription | PASS | WER=0.000 text='Please remind me to buy milk and eggs on the way home' |
| D07 | ASR | Fixture q3 transcription | PASS | WER=0.000 text='Parakey is a private voice keyboard that runs entirely on this phone.' |
| D08 | ASR | Fixture q4 transcription | PASS | WER=0.000 text='Hello world. This is a test of the speech recognition model' |
| D09 | ASR | Fixture q5 transcription | PASS | WER=0.000 text='I would like a large coffee with oat milk. please' |
| D10 | ASR | Long audio transcription | PASS | WER=0.019 audioMs=17795 |
| D11 | ASR | q1 real-time factor | PASS | RTF=0.052083333333333336 |
| D12 | ASR | q3 real-time factor | PASS | RTF=0.057383736741909164 |
| D13 | ASR | Long audio real-time factor | PASS | RTF=0.025793762292778872 |
| D14 | ASR | q3 latency | PASS | elapsedMs=211 |
| D16 | ASR | Empty audio is handled | PASS | {'ok': True, 'text': '', 'elapsedMs': 0, 'audioMs': 0, 'mode': 'empty'} |
| D17 | ASR | Very short audio is handled | PASS | {'ok': True, 'text': '', 'elapsedMs': 14, 'audioMs': 100, 'mode': 'short'} |
| D18 | ASR | AudioRecord captures 16 kHz samples | PASS | sampleCount=48000 |
| D19 | ASR | AudioRecord captures non-silence | PASS | loopback peak=1.0 samples=48000 attempts=1 |
| D20 | ASR | Mic key starts recording | PASS | voiceMode=True recording=True |
| D21 | ASR | Mic key stops recording | PASS | state={'voiceMode': False, 'recording': False, 'processing': False, 'clipboardMode': False, 'suggestions': True, 'micEnabled': True, 'page': 'LETTERS', 'shift': 'ON', 'width': 1080, 'height': 819, 'bottomInset': 63, 'numberRow': False, 'alternatePopup': False, 'alternateSelected': -1, 'cursorMode':  |
| D22 | ASR | Dictation inserts text | PASS | text='Parakey is a private voice keyboard that runs entirely on this phone.' |
| D23 | ASR | Dictation inserts at the cursor | PASS | text='Hello Parakey is a private voice keyboard that runs entirely on this phone. world' |
| D24 | ASR | Dictation adds one leading space | PASS | text='Hello Parakey is a private voice keyboard that runs entirely on this phone.' |
| D25 | ASR | Undo removes dictated text | PASS | after undo text='' |
| R01 | Real life | Compose a message with dictation | PASS | text='Hello Parakey is a private voice keyboard that runs entirely on this phone.Thanks' |
| R02 | Real life | Fill a contact form with the keyboard | PASS | name='Jordan' email='jordan@parakey.dev' |
| R03 | Real life | Password field masks and blocks voice | PASS | passwordAttr=true text='••••••' micEnabled=False |
| R04 | Real life | Clear-all button clears the form | PASS | before=['TestName', 'test@example.com', 'Hello'] after=['', '', '', ''] |
| R06 | Real life | Clipboard copy and paste work | PASS | first='FIRST_CLIP' second='COPY_SELECTION' |
| R09 | Real life | Random-tap monkey run is crash-free | PASS | pid=16973 crash=False |
| R10 | Real life | Relaunch keeps IME and model ready | PASS | default=com.parakey.keyboard/.ParakeyImeService modelReady=True shown=True |
| R12 | Real life | Zero app crashes in the full suite | PASS | no FATAL EXCEPTION from com.parakey.keyboard |
| R11 | Physical | Moto G85 speaker-loopback mic gate | FAIL | PENDING: no physical phone. Connect the Moto G85 with USB debugging (or wireless ADB) and rerun scripts/verify.py. |

## Failures

- **R11 Moto G85 speaker-loopback mic gate** — PENDING: no physical phone. Connect the Moto G85 with USB debugging (or wireless ADB) and rerun scripts/verify.py.

## Artifacts

- `verification/artifacts/clipboard-deadzone.png`
- `verification/artifacts/clipboard-panel.png`
- `verification/artifacts/dark-theme.png`
- `verification/artifacts/emulator-clipboard.png`
- `verification/artifacts/keyboard.png`
- `verification/artifacts/light-theme.png`
- `verification/artifacts/number-pad.png`
- `verification/artifacts/number-strip-20.png`
- `verification/artifacts/number-strip.png`
- `verification/artifacts/password.png`
- `verification/artifacts/physical-device.png`
- `verification/artifacts/theme-amoled-rose.png`
- `verification/artifacts/theme-dark-violet.png`
- `verification/artifacts/theme-light-sunset.png`
- `verification/artifacts/voice.png`
