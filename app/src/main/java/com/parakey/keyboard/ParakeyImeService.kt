package com.parakey.keyboard

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Toast
import com.parakey.keyboard.keyboard.KeyboardDimens
import com.parakey.keyboard.keyboard.KeyboardPage
import com.parakey.keyboard.keyboard.KeyboardTheme
import com.parakey.keyboard.keyboard.KeyboardView
import com.parakey.keyboard.keyboard.ShiftState
import com.parakey.keyboard.text.SuggestionEngine
import com.parakey.keyboard.text.TextPostProcessor
import com.parakey.keyboard.voice.AudioRecorder
import com.parakey.keyboard.voice.ModelRepository
import com.parakey.keyboard.voice.ParakeetEngine
import com.parakey.keyboard.voice.Transcription
import com.parakey.keyboard.voice.WavReader
import java.io.File
import java.util.concurrent.Executors

class ParakeyImeService : InputMethodService(), KeyboardView.Listener {

    companion object {
        @Volatile
        var instance: ParakeyImeService? = null
            private set
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "parakey-worker").apply { priority = Thread.NORM_PRIORITY }
    }

    private lateinit var keyboardView: KeyboardView
    private lateinit var settings: SettingsStore
    private lateinit var modelRepository: ModelRepository

    private val engine: ParakeetEngine by lazy { ParakeetEngine.get(this) }

    private var recorder: AudioRecorder? = null
    private var dictationActive = false
    private var transcribing = false
    private var generation = 0
    private var passwordField = false
    private var autoCorrectAllowed = true
    private var lastInsertedText: String? = null
    private var lastInsertedLength = 0
    private var lastCorrectionApplied: String? = null
    private var lastCorrectionRestore: String? = null
    private var correctionsApplied = 0
    private var suggestionsCommitted = 0

    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsStore(this)
        SuggestionEngine.initialize(this)
        modelRepository = ModelRepository(this)
        if (modelRepository.isInstalled()) {
            engine.preload()
        }
    }

    override fun onCreateInputView(): View {
        keyboardView = KeyboardView(this)
        keyboardView.listener = this
        applyAppearance()
        keyboardView.setOnApplyWindowInsetsListener { _, insets ->
            val bottom = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            keyboardView.setBottomInset(bottom)
            insets
        }
        return keyboardView
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        if (!::keyboardView.isInitialized || info == null) return
        val numeric = settings.numericPadEnabled && isNumericField(info)
        keyboardView.setNumericOnly(numeric)
        if (numeric) keyboardView.setMicEnabled(false, "Number pad")
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!::keyboardView.isInitialized) return
        applyAppearance()
        passwordField = info != null && isPasswordField(info)
        autoCorrectAllowed = info != null && allowsAutoCorrect(info)
        val showNumeric = settings.numericPadEnabled && info != null && isNumericField(info)
        keyboardView.setNumericOnly(showNumeric)
        keyboardView.hideClipboardMode()
        if (passwordField) {
            cancelDictation()
            keyboardView.setMicEnabled(false)
            keyboardView.setStatus(if (showNumeric) "Number pad" else "Voice typing is off in password fields")
        } else {
            keyboardView.setMicEnabled(true)
            keyboardView.setStatus(if (showNumeric) "Number pad" else "Tap the mic and speak")
        }
        keyboardView.showUndo(false)
        keyboardView.setShiftState(initialShift(info))
        refreshSuggestions()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (!dictationActive) refreshShiftAfterEdit()
        refreshSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        cancelDictation()
        super.onFinishInputView(finishingInput)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyAppearance()
    }

    override fun onWindowHidden() {
        cancelDictation()
        super.onWindowHidden()
    }

    private fun wordBeforeCursor(): String {
        val before = currentInputConnection?.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        return Regex("[A-Za-z']+$").find(before)?.value.orEmpty()
    }

    private fun refreshSuggestions() {
        if (!::keyboardView.isInitialized) return
        if (!settings.suggestionsEnabled || passwordField) {
            keyboardView.setSuggestions(emptyList())
            return
        }
        val before = currentInputConnection?.getTextBeforeCursor(96, 0)?.toString().orEmpty()
        val word = SuggestionEngine.currentWord(before)
        val previous = SuggestionEngine.previousWord(before)
        val personal = settings.personalWords
        val suggestions = SuggestionEngine.suggestions(word, previous, personal).toMutableList()
        if (word.length >= 2 && !SuggestionEngine.isKnown(word, personal) && suggestions.size < 3) {
            val addChip = "＋ $word"
            if (!suggestions.contains(addChip)) suggestions.add(addChip)
        }
        keyboardView.setSuggestions(suggestions.take(3))
    }

    override fun onSuggestion(text: String) {
        if (text.isBlank()) return
        if (text.startsWith("＋ ")) {
            val word = text.removePrefix("＋ ").trim()
            if (word.isNotEmpty()) {
                settings.addPersonalWord(word)
                toast("Added \"$word\" to your dictionary")
                refreshSuggestions()
            }
            return
        }
        val ic = currentInputConnection ?: return
        hideUndo()
        val word = wordBeforeCursor()
        if (word.isNotEmpty()) {
            ic.deleteSurroundingText(word.length, 0)
        }
        ic.commitText("$text ", 1)
        suggestionsCommitted++
        refreshShiftAfterEdit()
        refreshSuggestions()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        cancelDictation()
        if (::keyboardView.isInitialized) {
            keyboardView.setNumericOnly(false)
            keyboardView.hideVoiceMode()
            keyboardView.hideClipboardMode()
            keyboardView.setSuggestions(emptyList())
            keyboardView.showUndo(false)
        }
        lastInsertedText = null
        lastInsertedLength = 0
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onText(text: String) {
        if (text.isEmpty()) return
        hideUndo()
        val ic = currentInputConnection
        if (ic != null && settings.autocorrectEnabled && autoCorrectAllowed && text in setOf(".", ",", "!", "?")) {
            if (ic.getTextBeforeCursor(1, 0)?.toString() == " ") {
                ic.deleteSurroundingText(1, 0)
            }
            val word = wordBeforeCursor()
            val correction = SuggestionEngine.correct(word, settings.personalWords)
            if (correction != null && !correction.equals(word, ignoreCase = true)) {
                val applied = "$correction$text"
                val restore = "$word$text"
                ic.deleteSurroundingText(word.length, 0)
                ic.commitText(applied, 1)
                lastCorrectionApplied = applied
                lastCorrectionRestore = restore
                lastInsertedText = null
                lastInsertedLength = 0
                correctionsApplied++
                keyboardView.showUndo(true)
                keyboardView.setStatus("Auto-corrected · UNDO")
                refreshShiftAfterEdit()
                refreshSuggestions()
                return
            }
        }
        var output = text
        if (settings.smartPunctuation && autoCorrectAllowed && !passwordField && text == "\"") {
            val before = ic?.getTextBeforeCursor(1, 0)?.toString().orEmpty()
            output = if (before.isBlank() || before.lastOrNull()?.isWhitespace() == true) "\u201C" else "\u201D"
        }
        ic?.commitText(output, 1)
        if (keyboardView.currentPage() == KeyboardPage.EMOJI || text.codePoints().anyMatch { it >= 0x1F000 }) {
            settings.addEmojiRecent(text)
            keyboardView.setEmojiRecents(settings.emojiRecents)
        }
        refreshShiftAfterEdit()
        refreshSuggestions()
    }

    override fun onAlternate(text: String) {
        if (text.isEmpty()) return
        hideUndo()
        currentInputConnection?.commitText(text, 1)
        refreshShiftAfterEdit()
        refreshSuggestions()
    }

    override fun onDeleteWord() {
        val ic = currentInputConnection ?: return
        hideUndo()
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
            refreshShiftAfterEdit()
            refreshSuggestions()
            return
        }
        val before = ic.getTextBeforeCursor(256, 0)?.toString().orEmpty()
        val match = Regex("[\\p{L}\\p{N}'’]+\\s*$").find(before)
        val count = match?.value?.length?.coerceAtLeast(1) ?: 1
        ic.deleteSurroundingText(count, 0)
        refreshShiftAfterEdit()
        refreshSuggestions()
    }

    override fun onCursorMove(direction: Int) {
        val ic = currentInputConnection ?: return
        val keyCode = if (direction < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    override fun onBackspace() {
        val ic = currentInputConnection ?: return
        hideUndo()
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
        refreshShiftAfterEdit()
        refreshSuggestions()
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        hideUndo()
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noEnterAction = (info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        if (action != EditorInfo.IME_ACTION_NONE && !noEnterAction) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
        refreshShiftAfterEdit()
        refreshSuggestions()
    }

    override fun onSpace() {
        hideUndo()
        val ic = currentInputConnection ?: return
        if (settings.autocorrectEnabled && autoCorrectAllowed) {
            val word = wordBeforeCursor()
            val correction = SuggestionEngine.correct(word, settings.personalWords)
            if (correction != null && !correction.equals(word, ignoreCase = true)) {
                val applied = "$correction "
                val restore = "$word "
                ic.deleteSurroundingText(word.length, 0)
                ic.commitText(applied, 1)
                lastCorrectionApplied = applied
                lastCorrectionRestore = restore
                lastInsertedText = null
                lastInsertedLength = 0
                correctionsApplied++
                keyboardView.showUndo(true)
                keyboardView.setStatus("Auto-corrected · UNDO")
                refreshShiftAfterEdit()
                refreshSuggestions()
                return
            }
        }
        if (autoCorrectAllowed && ic.getTextBeforeCursor(1, 0)?.toString() == " ") {
            val two = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            val beforeSpace = two.firstOrNull()
            if (beforeSpace != null && (beforeSpace.isLetterOrDigit() || beforeSpace in ",;:")) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                refreshShiftAfterEdit()
                refreshSuggestions()
                return
            }
        }
        ic.commitText(" ", 1)
        refreshShiftAfterEdit()
        refreshSuggestions()
    }

    override fun onMic() {
        toggleDictation()
    }

    override fun onSettings() {
        openParakey()
    }

    override fun onUndo() {
        val corrected = lastCorrectionApplied
        val restoreText = lastCorrectionRestore
        if (corrected != null && restoreText != null) {
            val ic = currentInputConnection
            if (ic != null) {
                val before = ic.getTextBeforeCursor(corrected.length, 0)?.toString()
                if (before == corrected) {
                    ic.deleteSurroundingText(corrected.length, 0)
                    ic.commitText(restoreText, 1)
                    toast("Correction undone")
                } else {
                    toast("Can't undo — the text moved")
                }
            }
            lastCorrectionApplied = null
            lastCorrectionRestore = null
            keyboardView.showUndo(false)
            refreshSuggestions()
            return
        }
        val text = lastInsertedText ?: return
        val ic = currentInputConnection
        if (ic == null) {
            lastInsertedText = null
            keyboardView.showUndo(false)
            return
        }
        val before = ic.getTextBeforeCursor(lastInsertedLength, 0)?.toString()
        if (before == text) {
            ic.deleteSurroundingText(lastInsertedLength, 0)
            toast("Dictation removed")
        } else {
            toast("Can't undo — the text moved")
        }
        lastInsertedText = null
        lastInsertedLength = 0
        lastCorrectionApplied = null
        lastCorrectionRestore = null
        keyboardView.showUndo(false)
        keyboardView.setStatus("Tap the mic and speak")
    }

    override fun onCancelDictation() {
        cancelDictation()
    }

    override fun onClipboardRequest() {
        if (!::keyboardView.isInitialized) return
        if (dictationActive || transcribing) cancelDictation()
        refreshClipboardPanel()
        keyboardView.showClipboardMode()
    }

    private fun clipboardText(): String {
        val manager = getSystemService(ClipboardManager::class.java) ?: return ""
        val clip = manager.primaryClip ?: return ""
        if (clip.itemCount == 0) return ""
        return clip.getItemAt(0).coerceToText(this)?.toString().orEmpty().trim()
    }

    private fun refreshClipboardPanel() {
        if (!::keyboardView.isInitialized) return
        val selected = runCatching { currentInputConnection?.getSelectedText(0)?.toString() }.getOrNull()
        keyboardView.setClipboardData(
            items = if (passwordField) emptyList() else settings.clipboardHistory,
            preview = if (passwordField) "" else clipboardText(),
            canCopy = !passwordField && !selected.isNullOrEmpty(),
        )
    }

    override fun onClipboardPaste() {
        val text = clipboardText()
        if (text.isBlank()) {
            toast("The clipboard is empty")
            return
        }
        hideUndo()
        currentInputConnection?.commitText(text, 1)
        if (!passwordField) settings.addClipboardHistory(text)
        keyboardView.hideClipboardMode()
        refreshShiftAfterEdit()
        refreshSuggestions()
        toast("Pasted")
    }

    override fun onClipboardCopy() {
        if (passwordField) {
            toast("Copy is off in password fields")
            return
        }
        val selected = runCatching { currentInputConnection?.getSelectedText(0)?.toString() }.getOrNull()
        if (selected.isNullOrBlank()) {
            toast("Select some text first")
            return
        }
        val manager = getSystemService(ClipboardManager::class.java)
        manager?.setPrimaryClip(ClipData.newPlainText("Parakey", selected))
        settings.addClipboardHistory(selected)
        refreshClipboardPanel()
        toast("Copied")
    }

    override fun onClipboardSelectAll() {
        currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
        mainHandler.postDelayed({
            refreshClipboardPanel()
            val selected = runCatching { currentInputConnection?.getSelectedText(0)?.toString() }.getOrNull()
            toast(if (selected.isNullOrBlank()) "Nothing selected" else "Selected all")
        }, 150)
    }

    override fun onClipboardClear() {
        val manager = getSystemService(ClipboardManager::class.java)
        if (Build.VERSION.SDK_INT >= 28) {
            manager?.clearPrimaryClip()
        } else {
            manager?.setPrimaryClip(ClipData.newPlainText("", ""))
        }
        settings.clearClipboardHistory()
        refreshClipboardPanel()
        toast("Clipboard cleared")
    }

    override fun onClipboardItem(text: String) {
        if (text.isBlank()) return
        hideUndo()
        currentInputConnection?.commitText(text, 1)
        if (!passwordField) settings.addClipboardHistory(text)
        keyboardView.hideClipboardMode()
        refreshShiftAfterEdit()
        refreshSuggestions()
    }

    override fun onClipboardClose() {
        keyboardView.hideClipboardMode()
    }

    private fun hideUndo() {
        if (!::keyboardView.isInitialized) return
        lastInsertedText = null
        lastInsertedLength = 0
        keyboardView.showUndo(false)
        if (!keyboardView.isVoiceMode()) {
            keyboardView.setStatus(if (passwordField) "Voice typing is off in password fields" else "Tap the mic and speak")
        }
    }

    private fun allowsAutoCorrect(info: EditorInfo): Boolean {
        val type = info.inputType
        if (type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) return false
        val variation = type and InputType.TYPE_MASK_VARIATION
        return when (type and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS &&
                variation != InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS &&
                variation != InputType.TYPE_TEXT_VARIATION_URI &&
                variation != InputType.TYPE_TEXT_VARIATION_FILTER
            else -> false
        }
    }

    private fun isNumericField(info: EditorInfo): Boolean {
        return when (info.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER -> true
            else -> false
        }
    }

    private fun isPasswordField(info: EditorInfo): Boolean {
        val inputType = info.inputType
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private fun initialShift(info: EditorInfo?): ShiftState {
        if (info == null || !settings.autoCapitalizeEnabled) return ShiftState.OFF
        val caps = currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0
        return if (caps != 0) ShiftState.ON else ShiftState.OFF
    }

    private fun refreshShiftAfterEdit() {
        if (!::keyboardView.isInitialized) return
        if (!settings.autoCapitalizeEnabled) {
            // An explicit caps-lock is still the user's choice; only ordinary
            // auto-shift is disabled by this setting.
            if (keyboardView.shiftState() != ShiftState.CAPS) {
                keyboardView.setShiftState(ShiftState.OFF)
            }
            return
        }
        if (keyboardView.shiftState() == ShiftState.CAPS) return
        val info = currentInputEditorInfo ?: return
        val caps = currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0
        keyboardView.setShiftState(if (caps != 0) ShiftState.ON else ShiftState.OFF)
    }

    private fun toggleDictation() {
        if (passwordField) {
            toast("Voice typing is off in password fields")
            return
        }
        if (transcribing) {
            toast("Still transcribing…")
            return
        }
        if (dictationActive) {
            stopDictation()
            return
        }
        if (!hasMicrophonePermission()) {
            toast("Allow the microphone in Parakey to use voice typing")
            openParakey()
            return
        }
        if (!modelRepository.isInstalled()) {
            toast("Install the Parakeet speech model first")
            openParakey()
            return
        }
        dictationActive = true
        val requestGeneration = ++generation
        keyboardView.showVoiceMode()
        keyboardView.setStatus("Preparing model…")
        engine.preload { ok, error ->
            mainHandler.post {
                if (!dictationActive || requestGeneration != generation) return@post
                if (ok) {
                    startRecorder()
                } else {
                    failDictation(error ?: "The speech model could not load.")
                }
            }
        }
    }

    private fun startRecorder() {
        if (!dictationActive) return
        keyboardView.setStatus("Listening…")
        keyboardView.setRecording(true)
        requestAudioFocus()
        val newRecorder = AudioRecorder(
            onAmplitude = { level -> mainHandler.post { if (::keyboardView.isInitialized) keyboardView.addAmplitude(level) } },
            onError = { message ->
                mainHandler.post {
                    if (dictationActive) failDictation(message)
                }
            },
            onAutoStop = {
                mainHandler.post {
                    if (dictationActive) stopDictation()
                }
            },
        )
        recorder = newRecorder
        if (!newRecorder.start()) {
            recorder = null
            failDictation("The microphone could not start.")
        }
    }

    private fun stopDictation() {
        if (!dictationActive) return
        val current = recorder ?: run {
            failDictation("The microphone could not start.")
            return
        }
        dictationActive = false
        transcribing = true
        recorder = null
        abandonAudioFocus()
        keyboardView.setRecording(false)
        keyboardView.setProcessing(true)
        keyboardView.setStatus("Transcribing…")
        val currentGeneration = generation
        worker.execute {
            val recording = current.stop()
            if (recording == null || recording.samples.size < 1_600 || recording.durationMs < 250) {
                mainHandler.post {
                    if (currentGeneration == generation) failDictation("Didn't catch that — try again.")
                }
                return@execute
            }
            try {
                val transcription = engine.transcribe(recording.samples)
                mainHandler.post {
                    if (currentGeneration == generation) finishDictation(transcription)
                }
            } catch (t: Throwable) {
                mainHandler.post {
                    if (currentGeneration == generation) failDictation(t.message ?: "Transcription failed.")
                }
            }
        }
    }

    private fun finishDictation(transcription: Transcription) {
        transcribing = false
        if (!::keyboardView.isInitialized) return
        keyboardView.setProcessing(false)
        keyboardView.setSuggestions(emptyList())
        val text = TextPostProcessor.clean(transcription.text)
        if (text.isBlank()) {
            keyboardView.setStatus("Didn't catch that — try again.")
            mainHandler.postDelayed({
                if (!dictationActive && !transcribing) {
                    keyboardView.hideVoiceMode()
                    keyboardView.setStatus("Tap the mic and speak")
                }
            }, 1_400)
            return
        }
        val ic = currentInputConnection
        if (ic == null) {
            keyboardView.hideVoiceMode()
            keyboardView.setStatus("Tap the mic and speak")
            return
        }
        val insertion = TextPostProcessor.prependSpaceIfNeeded(ic.getTextBeforeCursor(1, 0), text)
        ic.commitText(insertion, 1)
        lastInsertedText = insertion
        lastInsertedLength = insertion.length
        lastCorrectionApplied = null
        lastCorrectionRestore = null
        keyboardView.setStatus("Inserted ${text.length} chars · ${transcription.elapsedMs} ms")
        keyboardView.showUndo(true)
        refreshShiftAfterEdit()
        mainHandler.postDelayed({
            if (!dictationActive && !transcribing && lastInsertedText != null) {
                keyboardView.hideVoiceMode()
                keyboardView.setStatus("Inserted · tap UNDO to remove")
            }
        }, 850)
    }

    private fun failDictation(message: String) {
        transcribing = false
        dictationActive = false
        recorder?.cancel()
        recorder = null
        abandonAudioFocus()
        if (!::keyboardView.isInitialized) return
        keyboardView.setRecording(false)
        keyboardView.setProcessing(false)
        keyboardView.setStatus(message)
        toast(message)
        mainHandler.postDelayed({
            if (!dictationActive && !transcribing && ::keyboardView.isInitialized) {
                keyboardView.hideVoiceMode()
                keyboardView.setStatus("Tap the mic and speak")
                keyboardView.showUndo(false)
            }
        }, 1_800)
    }

    private fun cancelDictation() {
        generation++
        dictationActive = false
        transcribing = false
        recorder?.cancel()
        recorder = null
        abandonAudioFocus()
        if (::keyboardView.isInitialized) {
            keyboardView.setRecording(false)
            keyboardView.setProcessing(false)
            keyboardView.hideVoiceMode()
            keyboardView.setStatus(if (passwordField) "Voice typing is off in password fields" else "Tap the mic and speak")
        }
    }

    private fun hasMicrophonePermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun openParakey() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun requestAudioFocus() {
        if (audioManager == null) {
            audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        }
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setOnAudioFocusChangeListener { change ->
                    if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        mainHandler.post { if (dictationActive) stopDictation() }
                    }
                }
                .build()
            focusRequest = request
            manager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest?.let { manager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(null)
        }
    }

    fun applyAppearance() {
        if (!::keyboardView.isInitialized) return
        keyboardView.setPalette(KeyboardTheme.palette(this, settings.theme, settings.accent))
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        keyboardView.setDimens(
            if (landscape) KeyboardDimens(row = 40f, topBar = 40f, letterSize = 18f, specialSize = 11f)
            else KeyboardDimens(),
        )
        keyboardView.setNumberRow(settings.numberRowEnabled)
        keyboardView.setEmojiRecents(settings.emojiRecents)
        keyboardView.setFeedback(settings.hapticsEnabled, settings.soundEnabled)
    }

    // ---- Debug hooks used by the verification harness (debug builds only expose the receiver). ----

    fun debugKeyMap(): String? = if (::keyboardView.isInitialized) keyboardView.debugKeyMap() else null

    fun debugState(): String? {
        if (!::keyboardView.isInitialized) return null
        val base = keyboardView.debugState().trimEnd('}')
        val palette = KeyboardTheme.palette(this, settings.theme, settings.accent)
        val (hapticEvents, soundEvents) = keyboardView.feedbackCounters()
        val paletteJson = listOf(
            "background" to palette.background,
            "key" to palette.key,
            "text" to palette.text,
            "hint" to palette.hint,
            "accent" to palette.accent,
            "onAccent" to palette.onAccent,
            "suggestionBg" to palette.suggestionBg,
            "suggestionText" to palette.suggestionText,
            "border" to palette.border,
        ).joinToString(",") { (name, color) ->
            "\"$name\":\"#${String.format("%06X", 0xFFFFFF and color)}\""
        }
        return "$base,\"theme\":\"${settings.theme}\",\"accent\":\"${settings.accent}\"," +
            "\"numberRow\":${settings.numberRowEnabled},\"numericPad\":${settings.numericPadEnabled},\"haptics\":${settings.hapticsEnabled}," +
            "\"emojiRecents\":[${settings.emojiRecents.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }}]," +
            "\"sound\":${settings.soundEnabled},\"hapticEvents\":$hapticEvents,\"soundEvents\":$soundEvents," +
            "\"palette\":{$paletteJson}," +
            "\"modelReady\":${modelRepository.isInstalled()}," +
            "\"passwordField\":$passwordField,\"editorInputType\":${currentInputEditorInfo?.inputType ?: 0}," +
            "\"transcribing\":$transcribing," +
            "\"autocorrect\":${settings.autocorrectEnabled},\"autoCorrectAllowed\":$autoCorrectAllowed," +
            "\"suggestions\":${settings.suggestionsEnabled}," +
            "\"autoCap\":${settings.autoCapitalizeEnabled},\"correctionsApplied\":$correctionsApplied," +
            "\"suggestionsCommitted\":$suggestionsCommitted,\"dictSize\":${SuggestionEngine.dictionarySize()}}"
    }

    fun debugMicRect(): android.graphics.RectF? = if (::keyboardView.isInitialized) keyboardView.micRectForDebug() else null

    fun debugStartDictation() = toggleDictation()

    fun debugStopDictation() = stopDictation()

    fun debugCancelDictation() = cancelDictation()

    fun debugInjectWav(path: String) {
        val base = getExternalFilesDir(null) ?: filesDir
        val file = if (path.startsWith("/")) File(path) else File(base, path)
        if (!file.isFile) {
            toast("Test WAV not found")
            return
        }
        if (!modelRepository.isInstalled()) {
            toast("Speech model not installed")
            return
        }
        cancelDictation()
        transcribing = true
        val requestGeneration = generation
        keyboardView.showVoiceMode()
        keyboardView.setProcessing(true)
        keyboardView.setStatus("Transcribing…")
        worker.execute {
            try {
                if (!engine.awaitReady(120_000)) {
                    throw IllegalStateException(engine.lastError() ?: "Speech model not ready")
                }
                val wav = WavReader.read(file)
                val transcription = engine.transcribe(wav.samples, wav.sampleRate)
                mainHandler.post {
                    if (requestGeneration == generation) finishDictation(transcription)
                }
            } catch (t: Throwable) {
                mainHandler.post {
                    if (requestGeneration == generation) failDictation(t.message ?: "Test transcription failed")
                }
            }
        }
    }

    fun debugInsertText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    fun debugSetTheme(theme: String) {
        settings.theme = theme
        applyAppearance()
    }

    fun debugSetAccent(accent: String) {
        settings.accent = accent
        applyAppearance()
    }

    fun debugSetNumericPad(enabled: Boolean) {
        settings.numericPadEnabled = enabled
        applyAppearance()
    }

    fun debugForceNumeric(enabled: Boolean) {
        settings.numericPadEnabled = enabled
        if (::keyboardView.isInitialized) {
            keyboardView.setNumericOnly(enabled)
            keyboardView.setMicEnabled(!enabled, if (enabled) "Number pad" else null)
        }
    }

    fun debugSetNumberRow(enabled: Boolean) {
        settings.numberRowEnabled = enabled
        applyAppearance()
    }

    fun debugSetFeedback(haptics: Boolean, sound: Boolean) {
        settings.hapticsEnabled = haptics
        settings.soundEnabled = sound
        applyAppearance()
        if (::keyboardView.isInitialized) keyboardView.resetFeedbackCounters()
    }

    fun debugResetFeedback() {
        if (::keyboardView.isInitialized) keyboardView.resetFeedbackCounters()
    }

    fun debugEditorText(): String? {
        val ic = currentInputConnection ?: return null
        val before = ic.getTextBeforeCursor(20_000, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(20_000, 0)?.toString().orEmpty()
        return before + after
    }

    fun debugCorrect(word: String): String? =
        SuggestionEngine.correct(word, settings.personalWords)

    fun debugResetInputState() {
        if (!::keyboardView.isInitialized) return
        keyboardView.setShiftState(ShiftState.OFF)
        keyboardView.setPage(KeyboardPage.LETTERS)
        keyboardView.setSuggestions(emptyList())
    }

    fun debugSetAutocorrect(enabled: Boolean) {
        settings.autocorrectEnabled = enabled
        refreshSuggestions()
    }

    fun debugSetSuggestions(enabled: Boolean) {
        settings.suggestionsEnabled = enabled
        refreshSuggestions()
    }

    fun debugSetAutoCapitalize(enabled: Boolean) {
        settings.autoCapitalizeEnabled = enabled
        if (!enabled) keyboardView.setShiftState(ShiftState.OFF)
    }

    fun debugAddPersonalWord(word: String) {
        settings.addPersonalWord(word)
        refreshSuggestions()
    }

    fun debugClearInput() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(10_000, 0)?.length ?: 0
        if (before > 0) ic.deleteSurroundingText(before, 0)
    }
}
