package com.parakey.keyboard.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.parakey.keyboard.ui.colorWithAlpha
import com.parakey.keyboard.ui.dp
import com.parakey.keyboard.ui.sp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Listener {
        fun onText(text: String)
        fun onBackspace()
        fun onEnter()
        fun onSpace()
        fun onMic()
        fun onSettings()
        fun onUndo()
        fun onCancelDictation()
        fun onClipboardRequest()
        fun onClipboardPaste()
        fun onClipboardCopy()
        fun onClipboardSelectAll()
        fun onClipboardClear()
        fun onClipboardItem(text: String)
        fun onClipboardClose()
        fun onSuggestion(text: String)
        fun onAlternate(text: String)
        fun onCursorMove(direction: Int)
        fun onDeleteWord()
    }

    var listener: Listener? = null
    var palette: KeyboardPalette = KeyboardTheme.palette(context)
        private set
    var dimens: KeyboardDimens = KeyboardDimens()
        private set

    private var page = KeyboardPage.LETTERS
    private var shift = ShiftState.OFF
    private var micEnabled = true
    private var recording = false
    private var processing = false
    private var voiceMode = false
    private var statusText = "Tap the mic and speak"
    private var liveText = ""
    private var undoAvailable = false
    private var bottomInset = 0
    private var clipboardMode = false
    private var clipboardItems: List<String> = emptyList()
    private var clipboardPreview = ""
    private var clipboardCanCopy = true
    private var suggestions: List<String> = emptyList()
    private var numberRow = false
    private var emojiRecents: List<String> = emptyList()
    private var hapticsEnabled = true
    private var soundEnabled = true
    private var hapticEvents = 0
    private var soundEvents = 0
    private val suggestionRects = mutableListOf<RectF>()

    private val amplitudes = FloatArray(28)
    private var amplitudeIndex = 0
    private var pulseStart = 0L
    private var spinnerStart = 0L

    private var keyRects: List<Pair<Key, RectF>> = emptyList()
    private var keyTouchRects: List<Pair<Key, RectF>> = emptyList()
    private val keyById = mutableMapOf<String, RectF>()
    private val secondaryPresses = mutableMapOf<Int, Pair<Key, RectF>>()
    private var pressedSuggestionText: String? = null
    private var pressedSuggestionRect: RectF? = null
    private var lastShiftTapTime = 0L
    private var micRect = RectF()
    private var gearRect = RectF()
    private var clipRect = RectF()
    private var suggestionRect = RectF()
    private var undoRect = RectF()
    private var cancelRect = RectF()
    private val voiceMicCenter = PointF()
    private val clipboardRects = LinkedHashMap<String, RectF>()

    private var pressedKey: Key? = null
    private var pressedRect: RectF? = null
    private var longPressRunnable: Runnable? = null
    private var alternatePopup: List<String>? = null
    private var alternateSelected = -1
    private var alternateKey: Key? = null
    private val popupCells = mutableListOf<RectF>()
    private val popupBounds = RectF()
    private var cursorMode = false
    private var cursorAnchorX = 0f
    private var backspaceWordDeleted = false
    private var pressStartedOnBackspace = false
    private var lastDownX = 0f
    private var lastDownY = 0f
    private var pressedTopBar: String? = null
    private var activePointerId = -1
    private var pressedPointerId = -1
    private var longPressFired = false
    private val handler = Handler(Looper.getMainLooper())

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (pressedKey?.action == KeyAction.BACKSPACE && !voiceMode) {
                listener?.onBackspace()
                handler.postDelayed(this, 55L)
            }
        }
    }

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Parakey keyboard"
        setWillNotDraw(false)
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
    }

    fun setPalette(next: KeyboardPalette) {
        palette = next
        invalidate()
    }

    fun setEmojiRecents(next: List<String>) {
        if (emojiRecents == next) return
        emojiRecents = next.take(8)
        if (page == KeyboardPage.EMOJI) {
            requestLayout()
            invalidate()
        }
    }

    fun setNumberRow(enabled: Boolean) {
        if (numberRow == enabled) return
        numberRow = enabled
        clearModalGesture()
        requestLayout()
        invalidate()
    }

    fun setFeedback(haptics: Boolean, sound: Boolean) {
        hapticsEnabled = haptics
        soundEnabled = sound
    }

    fun resetFeedbackCounters() {
        hapticEvents = 0
        soundEvents = 0
    }

    fun feedbackCounters(): Pair<Int, Int> = hapticEvents to soundEvents

    fun setDimens(next: KeyboardDimens) {
        dimens = next
        requestLayout()
        invalidate()
    }

    fun setBottomInset(px: Int) {
        if (bottomInset != px) {
            bottomInset = px
            requestLayout()
            invalidate()
        }
    }

    fun setPage(next: KeyboardPage) {
        if (page == next) return
        page = next
        clearModalGesture()
        pressedKey = null
        secondaryPresses.clear()
        requestLayout()
        invalidate()
    }

    fun currentPage(): KeyboardPage = page

    fun setShiftState(next: ShiftState) {
        if (next == ShiftState.OFF) lastShiftTapTime = 0L
        if (shift == next) return
        shift = next
        invalidate()
    }

    fun shiftState(): ShiftState = shift

    fun setMicEnabled(enabled: Boolean, reason: String? = null) {
        micEnabled = enabled
        if (!enabled) {
            statusText = reason ?: "Voice typing is off in password fields"
            if (voiceMode) {
                cancelVoiceMode()
            }
        }
        invalidate()
    }

    fun setStatus(text: String) {
        statusText = text
        invalidate()
    }

    fun setLiveText(text: String) {
        liveText = text
        if (voiceMode) invalidate()
    }

    fun setRecording(value: Boolean) {
        recording = value
        processing = false
        if (value) {
            pulseStart = SystemClock.uptimeMillis()
            spinnerStart = 0L
            liveText = ""
            for (i in amplitudes.indices) amplitudes[i] = 0f
        }
        invalidate()
    }

    fun setProcessing(value: Boolean) {
        processing = value
        recording = false
        if (value) spinnerStart = SystemClock.uptimeMillis()
        invalidate()
    }

    fun showVoiceMode() {
        voiceMode = true
        clipboardMode = false
        recording = false
        processing = false
        liveText = ""
        statusText = "Preparing model…"
        invalidate()
    }

    fun hideVoiceMode() {
        voiceMode = false
        recording = false
        processing = false
        liveText = ""
        invalidate()
    }

    fun isVoiceMode(): Boolean = voiceMode

    fun showUndo(available: Boolean) {
        undoAvailable = available
        invalidate()
    }

    fun addAmplitude(level: Float) {
        amplitudes[amplitudeIndex] = level.coerceIn(0f, 1f)
        amplitudeIndex = (amplitudeIndex + 1) % amplitudes.size
        if (recording) invalidate()
    }

    fun getStatus(): String = statusText

    fun setSuggestions(items: List<String>) {
        if (suggestions == items) return
        if (pressedTopBar == "suggestion") {
            pressedTopBar = null
            pressedSuggestionText = null
            pressedSuggestionRect = null
        }
        suggestions = items
        suggestionRects.clear()
        requestLayout()
        invalidate()
    }

    fun setClipboardData(items: List<String>, preview: String, canCopy: Boolean) {
        clipboardItems = items
        clipboardPreview = preview
        clipboardCanCopy = canCopy
        if (clipboardMode) invalidate()
    }

    fun showClipboardMode() {
        clipboardMode = true
        voiceMode = false
        recording = false
        processing = false
        invalidate()
    }

    fun hideClipboardMode() {
        clipboardMode = false
        invalidate()
    }

    fun isClipboardMode(): Boolean = clipboardMode

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desired = desiredHeight(width)
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else {
            desired
        }
        setMeasuredDimension(width, height)
    }

    private fun desiredHeight(width: Int): Int {
        val spec = KeyboardLayouts.specFor(page, numberRow, emojiRecents)
        val density = resources.displayMetrics.density
        val row = dimens.row * density
        val gap = dimens.gap * density
        val top = dimens.topBar * density
        val bottomPad = 6f * density
        return (top + 4f * density + spec.rows.size * row + (spec.rows.size - 1) * gap + bottomPad + bottomInset).toInt()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutGeometry(w, h)
        val options = alternatePopup
        val key = alternateKey
        if (options != null && key != null) {
            val rect = keyById[key.stableId]
            if (rect != null) layoutPopup(rect, options.size) else clearModalGesture()
        }
    }

    private fun layoutGeometry(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val density = resources.displayMetrics.density
        val spec = KeyboardLayouts.specFor(page, numberRow, emojiRecents)
        val topBar = dimens.topBar * density
        val contentTop = topBar + 4f * density
        val gap = dimens.gap * density
        val sidePad = dimens.sidePad * density
        val rowCount = spec.rows.size.coerceAtLeast(1)
        val availableHeight = (height - contentTop - 6f * density - bottomInset).coerceAtLeast(rowCount * 32f * density)
        val rowHeight = ((availableHeight - (rowCount - 1) * gap) / rowCount).coerceAtLeast(30f * density)

        val newRects = ArrayList<Pair<Key, RectF>>(40)
        val rows = ArrayList<MutableList<Pair<Key, RectF>>>(spec.rows.size)
        keyById.clear()
        var y = contentTop
        for (row in spec.rows) {
            val totalWeight = row.keys.sumOf { it.weight.toDouble() }.toFloat() + row.sideInset * 2f
            val usable = width - 2f * sidePad - gap * (row.keys.size - 1)
            val unit = usable / totalWeight
            var x = sidePad + row.sideInset * unit
            val rowRects = ArrayList<Pair<Key, RectF>>(row.keys.size)
            for (key in row.keys) {
                val w = unit * key.weight
                val rect = RectF(x, y, x + w, y + rowHeight)
                newRects.add(key to rect)
                rowRects.add(key to rect)
                keyById[key.stableId] = rect
                x += w + gap
            }
            rows.add(rowRects)
            y += rowHeight + gap
        }
        keyRects = newRects
        // Tile the whole key area: adjacent keys meet at the midpoint of the
        // visual gap, first/last keys reach the view edge, and rows meet at
        // their vertical midpoint. This removes dead strips and gap ties.
        val touchRects = ArrayList<Pair<Key, RectF>>(newRects.size)
        for ((rowIndex, rowRects) in rows.withIndex()) {
            for ((index, entry) in rowRects.withIndex()) {
                val (key, rect) = entry
                val left = if (index == 0) 0f else (rowRects[index - 1].second.right + rect.left) / 2f
                val right = if (index == rowRects.lastIndex) width.toFloat() else (rect.right + rowRects[index + 1].second.left) / 2f
                val top = if (rowIndex == 0) contentTop else (rows[rowIndex - 1].first().second.bottom + rect.top) / 2f
                val bottom = if (rowIndex == rows.lastIndex) height.toFloat() else (rect.bottom + rows[rowIndex + 1].first().second.top) / 2f
                touchRects.add(key to RectF(left, top, right, bottom))
            }
        }
        keyTouchRects = touchRects

        val micCx = width - dimens.topBarMicCenterFromRight * density
        val gearCx = width - dimens.topBarGearCenterFromRight * density
        val clipCx = width - 112f * density
        val topCy = topBar / 2f
        val micR = dimens.micRadius * density
        micRect = RectF(micCx - micR, topCy - micR, micCx + micR, topCy + micR)
        gearRect = RectF(gearCx - micR, topCy - micR, gearCx + micR, topCy + micR)
        clipRect = RectF(clipCx - micR, topCy - micR, clipCx + micR, topCy + micR)

        val suggestionTop = (topBar - dimens.suggestionHeight * density) / 2f
        suggestionRect = RectF(
            sidePad + 4f * density,
            suggestionTop,
            max(sidePad + 40f * density, clipCx - micR - 8f * density),
            suggestionTop + dimens.suggestionHeight * density,
        )
        layoutClipboardRects(width, height, density)

        suggestionRects.clear()
        if (suggestions.isNotEmpty() && !undoAvailable && !voiceMode && !clipboardMode) {
            val chipGap = 6f * density
            val innerPad = 5f * density
            val chipWidth = (suggestionRect.width() - innerPad * 2f - chipGap * (suggestions.size - 1)) / suggestions.size
            val chipHeight = 32f * density
            val top = suggestionRect.centerY() - chipHeight / 2f
            for (i in suggestions.indices) {
                val left = suggestionRect.left + innerPad + i * (chipWidth + chipGap)
                suggestionRects.add(RectF(left, top, left + chipWidth, top + chipHeight))
            }
        }
        undoRect = RectF(suggestionRect.right - 52f * density, suggestionRect.top, suggestionRect.right - 4f * density, suggestionRect.bottom)

        cancelRect = RectF(16f * density, 12f * density, 16f * density + 82f * density, 12f * density + 38f * density)
        voiceMicCenter.set(width / 2f, height * 0.34f)
    }

    private fun layoutClipboardRects(width: Int, height: Int, density: Float) {
        clipboardRects.clear()
        val pad = 16f * density
        val closeSize = 36f * density
        clipboardRects["close"] = RectF(width - pad - closeSize, 8f * density, width - pad, 8f * density + closeSize)
        val gap = 6f * density
        val buttonW = (width - 2f * pad - 3f * gap) / 4f
        val buttonY = 42f * density
        val buttonH = 34f * density
        clipboardRects["paste"] = RectF(pad, buttonY, pad + buttonW, buttonY + buttonH)
        clipboardRects["copy"] = RectF(pad + buttonW + gap, buttonY, pad + 2f * buttonW + gap, buttonY + buttonH)
        clipboardRects["selectAll"] = RectF(pad + 2f * buttonW + 2f * gap, buttonY, pad + 3f * buttonW + 2f * gap, buttonY + buttonH)
        clipboardRects["clear"] = RectF(pad + 3f * buttonW + 3f * gap, buttonY, width - pad, buttonY + buttonH)
        clipboardRects["item0"] = RectF(pad, 200f * density, width - pad, 246f * density)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        layoutGeometry(width, height)
        when {
            voiceMode -> drawVoicePanel(canvas)
            clipboardMode -> drawClipboardPanel(canvas)
            else -> drawKeyboard(canvas)
        }
    }

    private fun drawKeyboard(canvas: Canvas) {
        canvas.drawColor(palette.background)
        drawTopBar(canvas)
        val density = resources.displayMetrics.density
        val radius = dimens.keyRadius * density

        for ((key, rect) in keyRects) {
            val pressed = pressedKey?.stableId == key.stableId
            val bg = when {
                key.accent -> if (pressed) palette.accentPressed else palette.accent
                pressed -> if (key.special) palette.specialPressed else palette.keyPressed
                key.special -> palette.special
                else -> palette.key
            }
            if (!key.special) {
                paint.color = palette.shadow
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(
                    rect.left,
                    rect.top + 2f * density,
                    rect.right,
                    rect.bottom + 2f * density,
                    radius,
                    radius,
                    paint,
                )
            }
            paint.color = bg
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(rect, radius, radius, paint)

            val label = KeyboardLayouts.displayLabel(key, page, shift)
            if (key.icon != KeyIcon.NONE) {
                val iconColor = when {
                    key.accent -> palette.onAccent
                    key.action == KeyAction.SHIFT && shift != ShiftState.OFF -> palette.accent
                    else -> palette.text
                }
                val drawnIcon = if (key.action == KeyAction.SHIFT && shift == ShiftState.CAPS) KeyIcon.CAPS else key.icon
                drawIcon(canvas, drawnIcon, rect.centerX(), rect.centerY(), min(rect.width(), rect.height()) * 0.38f, iconColor)
            } else if (label.isNotEmpty()) {
                textPaint.color = if (key.accent) palette.onAccent else palette.text
                textPaint.typeface = if (key.special) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.create("sans-serif", Typeface.NORMAL)
                textPaint.textSize = when {
                    page == KeyboardPage.EMOJI -> dimens.emojiSize * density
                    key.action == KeyAction.SPACE -> 11f * density
                    key.special -> dimens.specialSize * density
                    else -> dimens.letterSize * density
                }
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.isFakeBoldText = key.action == KeyAction.SPACE
                val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(label, rect.centerX(), baseline, textPaint)
            }
        }

        if (alternatePopup != null) {
            drawAlternatePopup(canvas)
        } else {
            drawKeyPreview(canvas)
        }
        if (cursorMode) drawCursorHint(canvas)
    }

    private fun drawTopBar(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val radius = dimens.suggestionHeight * density / 2f
        paint.color = palette.suggestionBg
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(suggestionRect, radius, radius, paint)

        if (suggestionRects.isNotEmpty() && !undoAvailable) {
            for (i in suggestionRects.indices) {
                val rect = suggestionRects[i]
                paint.color = colorWithAlpha(palette.accent, 22)
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(rect, 8f * density, 8f * density, paint)
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.color = palette.text
                textPaint.textSize = 11.5f * density
                textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                val label = TextUtils.ellipsize(suggestions[i], textPaint, rect.width() - 8f * density, TextUtils.TruncateAt.END).toString()
                canvas.drawText(label, rect.centerX(), rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
            }
        } else {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 12.5f * density
            textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textPaint.color = palette.suggestionText
            val left = suggestionRect.left + 14f * density
            val right = if (undoAvailable) undoRect.left - 4f * density else suggestionRect.right - 8f * density
            val avail = (right - left).coerceAtLeast(0f)
            val shown = TextUtils.ellipsize(statusText, textPaint, avail, TextUtils.TruncateAt.END).toString()
            val baseline = suggestionRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(shown, left, baseline, textPaint)
        }

        if (undoAvailable) {
            val undoBg = RectF(undoRect)
            paint.color = colorWithAlpha(palette.accent, 28)
            canvas.drawRoundRect(undoBg, undoBg.height() / 2f, undoBg.height() / 2f, paint)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = palette.accent
            textPaint.textSize = 11f * density
            textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            canvas.drawText("UNDO", undoBg.centerX(), undoBg.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        }

        paint.color = if (micEnabled) palette.accent else palette.special
        canvas.drawCircle(micRect.centerX(), micRect.centerY(), micRect.width() / 2f, paint)
        drawIcon(
            canvas,
            KeyIcon.MIC,
            micRect.centerX(),
            micRect.centerY(),
            micRect.width() * 0.56f,
            if (micEnabled) palette.onAccent else palette.hint,
        )
        if (!micEnabled) {
            paint.color = palette.hint
            paint.strokeWidth = 2f * density
            paint.style = Paint.Style.STROKE
            canvas.drawLine(
                micRect.left + micRect.width() * 0.2f,
                micRect.bottom - micRect.height() * 0.2f,
                micRect.right - micRect.width() * 0.2f,
                micRect.top + micRect.height() * 0.2f,
                paint,
            )
        }

        drawIcon(canvas, KeyIcon.CLIP, clipRect.centerX(), clipRect.centerY(), clipRect.width() * 0.52f, palette.hint)
        drawIcon(canvas, KeyIcon.GEAR, gearRect.centerX(), gearRect.centerY(), gearRect.width() * 0.52f, palette.hint)
    }

    private fun drawClipboardPanel(canvas: Canvas) {
        val density = resources.displayMetrics.density
        canvas.drawColor(palette.overlay)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = palette.text
        textPaint.textSize = 22f * density
        textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        canvas.drawText("Clipboard", 16f * density, 30f * density, textPaint)

        val close = clipboardRects["close"] ?: RectF()
        paint.color = colorWithAlpha(palette.hint, 30)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(close.centerX(), close.centerY(), close.width() / 2f, paint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = palette.text
        textPaint.textSize = 17f * density
        canvas.drawText("×", close.centerX(), close.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)

        val labels = mapOf("paste" to "Paste", "copy" to "Copy", "selectAll" to "All", "clear" to "Clear")
        for ((name, label) in labels) {
            val rect = clipboardRects[name] ?: continue
            paint.color = when (name) {
                "paste" -> palette.accent
                "clear" -> colorWithAlpha(palette.danger, 24)
                "copy" -> if (clipboardCanCopy) palette.special else colorWithAlpha(palette.special, 120)
                else -> palette.special
            }
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(rect, 10f * density, 10f * density, paint)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 11f * density
            textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textPaint.color = when {
                name == "paste" -> palette.onAccent
                name == "clear" -> palette.danger
                name == "copy" && !clipboardCanCopy -> palette.hint
                else -> palette.text
            }
            canvas.drawText(label, rect.centerX(), rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        }

        val previewCard = RectF(16f * density, 84f * density, width - 16f * density, 174f * density)
        paint.color = palette.suggestionBg
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(previewCard, 12f * density, 12f * density, paint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = palette.hint
        textPaint.textSize = 9f * density
        textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        canvas.drawText("CURRENT CLIPBOARD", previewCard.left + 12f * density, previewCard.top + 18f * density, textPaint)
        textPaint.color = if (clipboardPreview.isBlank()) palette.hint else palette.text
        textPaint.textSize = 12f * density
        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        drawWrappedText(
            canvas,
            clipboardPreview.ifBlank { "No text on the clipboard" },
            previewCard.left + 12f * density,
            previewCard.top + 38f * density,
            previewCard.width() - 24f * density,
            16f * density,
            3,
        )

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = palette.hint
        textPaint.textSize = 9f * density
        textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        canvas.drawText("RECENT", 16f * density, 190f * density, textPaint)

        val item = clipboardItems.firstOrNull()
        val itemRect = clipboardRects["item0"] ?: RectF()
        if (item.isNullOrBlank()) {
            textPaint.color = palette.hint
            textPaint.textSize = 11f * density
            textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            canvas.drawText("Copy or paste something to build history.", 16f * density, 220f * density, textPaint)
        } else {
            paint.color = palette.suggestionBg
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(itemRect, 10f * density, 10f * density, paint)
            textPaint.color = palette.text
            textPaint.textSize = 11f * density
            textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            val shown = TextUtils.ellipsize(item.replace("\n", " "), textPaint, itemRect.width() - 76f * density, TextUtils.TruncateAt.END).toString()
            canvas.drawText(shown, itemRect.left + 10f * density, itemRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
            if (clipboardItems.size > 1) {
                textPaint.color = palette.hint
                textPaint.textSize = 9f * density
                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText("+${clipboardItems.size - 1}", itemRect.right - 10f * density, itemRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
            }
        }
    }

    private fun drawWrappedText(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        lineHeight: Float,
        maxLines: Int,
    ) {
        var remaining = value.trim()
        var line = 0
        var baseline = y
        while (remaining.isNotEmpty() && line < maxLines) {
            val count = textPaint.breakText(remaining, true, maxWidth, null)
            if (count <= 0) break
            canvas.drawText(remaining.substring(0, count), x, baseline, textPaint)
            remaining = remaining.substring(count).trimStart()
            baseline += lineHeight
            line++
        }
    }

    private fun drawKeyPreview(canvas: Canvas) {
        val key = pressedKey ?: return
        val rect = pressedRect ?: return
        if (key.action != KeyAction.CHARACTER || key.special || page == KeyboardPage.EMOJI) return
        if (rect.top < 56f * resources.displayMetrics.density) return
        val density = resources.displayMetrics.density
        val radius = 8f * density
        val previewW = max(rect.width(), 42f * density)
        val previewH = 46f * density
        val cx = rect.centerX()
        val top = rect.top - previewH - 6f * density
        val preview = RectF(cx - previewW / 2f, top, cx + previewW / 2f, top + previewH)
        paint.color = palette.shadow
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(
            preview.left,
            preview.top + 3f * density,
            preview.right,
            preview.bottom + 3f * density,
            radius,
            radius,
            paint,
        )
        paint.color = palette.key
        canvas.drawRoundRect(preview, radius, radius, paint)
        textPaint.color = palette.text
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 26f * density
        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textPaint.isFakeBoldText = false
        val label = KeyboardLayouts.displayLabel(key, page, shift)
        canvas.drawText(label, preview.centerX(), preview.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
    }

    private fun drawVoicePanel(canvas: Canvas) {
        val density = resources.displayMetrics.density
        canvas.drawColor(palette.overlay)

        val cancelBg = RectF(cancelRect)
        paint.color = colorWithAlpha(palette.danger, 24)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(cancelBg, cancelBg.height() / 2f, cancelBg.height() / 2f, paint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = palette.danger
        textPaint.textSize = 12f * density
        textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        canvas.drawText(
            "Cancel",
            cancelBg.centerX(),
            cancelBg.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f,
            textPaint,
        )

        val now = SystemClock.uptimeMillis()
        val cx = voiceMicCenter.x
        val cy = voiceMicCenter.y
        val baseRadius = dimens.voiceMicRadius * density
        if (recording) {
            val t = ((now - pulseStart) % 1600L) / 1600f
            for (i in 0..2) {
                val phase = (t + i / 3f) % 1f
                val ringRadius = baseRadius * (1.0f + phase * 0.55f)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f * density
                paint.color = colorWithAlpha(palette.accent, ((1f - phase) * 70f).toInt())
                canvas.drawCircle(cx, cy, ringRadius, paint)
            }
        }

        paint.style = Paint.Style.FILL
        paint.color = when {
            processing -> palette.warning
            recording -> palette.accent
            else -> palette.accent
        }
        canvas.drawCircle(cx, cy, baseRadius, paint)

        if (processing) {
            val start = ((now - spinnerStart) % 1000L) / 1000f * 360f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f * density
            paint.color = colorWithAlpha(palette.onAccent, 90)
            canvas.drawCircle(cx, cy, baseRadius * 0.68f, paint)
            paint.color = palette.onAccent
            canvas.drawArc(
                cx - baseRadius * 0.68f,
                cy - baseRadius * 0.68f,
                cx + baseRadius * 0.68f,
                cy + baseRadius * 0.68f,
                start,
                90f,
                false,
                paint,
            )
        } else {
            drawIcon(canvas, KeyIcon.MIC, cx, cy, baseRadius * 0.9f, palette.onAccent)
        }

        val bars = 21
        val barGap = 5f * density
        val barWidth = 4f * density
        val totalWidth = bars * barWidth + (bars - 1) * barGap
        val startX = cx - totalWidth / 2f + barWidth / 2f
        val barsBaseline = cy + baseRadius + 34f * density
        paint.style = Paint.Style.FILL
        for (i in 0 until bars) {
            val sample = amplitudes[(amplitudeIndex + i * amplitudes.size / bars) % amplitudes.size]
            val h = (4f * density) + sample * 34f * density
            paint.color = colorWithAlpha(if (processing) palette.warning else palette.accent, 190)
            canvas.drawRoundRect(
                startX + i * (barWidth + barGap) - barWidth / 2f,
                barsBaseline - h / 2f,
                startX + i * (barWidth + barGap) + barWidth / 2f,
                barsBaseline + h / 2f,
                barWidth / 2f,
                barWidth / 2f,
                paint,
            )
        }

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = palette.text
        textPaint.textSize = 16f * density
        textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val headline = when {
            processing -> "Transcribing…"
            recording -> "Listening…"
            else -> statusText
        }
        canvas.drawText(headline, cx, barsBaseline + 42f * density, textPaint)

        textPaint.color = palette.hint
        textPaint.textSize = 12f * density
        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        val hint = when {
            processing -> "Keep the keyboard open"
            recording -> "Tap the mic to stop"
            else -> "Tap the mic to start"
        }
        canvas.drawText(hint, cx, barsBaseline + 65f * density, textPaint)

        val preview = liveText.trim()
        if (preview.isNotEmpty()) {
            textPaint.color = palette.text
            textPaint.textSize = 14f * density
            textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            val maxWidth = width - 48f * density
            val lineHeight = 20f * density
            var remaining = preview
            var line = 0
            var y = barsBaseline + 100f * density
            while (remaining.isNotEmpty() && line < 3 && y < height - 20f * density) {
                val count = textPaint.breakText(remaining, true, maxWidth, null)
                if (count <= 0) break
                val piece = remaining.substring(0, count)
                canvas.drawText(piece, cx, y, textPaint)
                remaining = remaining.substring(count).trimStart()
                y += lineHeight
                line++
            }
        }

        if (recording || processing) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawIcon(canvas: Canvas, icon: KeyIcon, cx: Float, cy: Float, size: Float, color: Int) {
        val density = resources.displayMetrics.density
        paint.color = color
        paint.strokeWidth = max(1.6f * density, size * 0.075f)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        when (icon) {
            KeyIcon.NONE -> Unit
            KeyIcon.SHIFT, KeyIcon.CAPS -> {
                paint.style = if (icon == KeyIcon.CAPS) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL
                path.reset()
                path.moveTo(cx, cy - size * 0.48f)
                path.lineTo(cx + size * 0.48f, cy + size * 0.02f)
                path.lineTo(cx + size * 0.19f, cy + size * 0.02f)
                path.lineTo(cx + size * 0.19f, cy + size * 0.46f)
                path.lineTo(cx - size * 0.19f, cy + size * 0.46f)
                path.lineTo(cx - size * 0.19f, cy + size * 0.02f)
                path.lineTo(cx - size * 0.48f, cy + size * 0.02f)
                path.close()
                canvas.drawPath(path, paint)
                if (icon == KeyIcon.CAPS) {
                    paint.style = Paint.Style.STROKE
                    canvas.drawLine(cx - size * 0.36f, cy + size * 0.62f, cx + size * 0.36f, cy + size * 0.62f, paint)
                }
            }
            KeyIcon.BACKSPACE -> {
                path.reset()
                path.moveTo(cx + size * 0.42f, cy - size * 0.36f)
                path.lineTo(cx - size * 0.08f, cy - size * 0.36f)
                path.lineTo(cx - size * 0.5f, cy)
                path.lineTo(cx - size * 0.08f, cy + size * 0.36f)
                path.lineTo(cx + size * 0.42f, cy + size * 0.36f)
                canvas.drawPath(path, paint)
                canvas.drawLine(cx + size * 0.02f, cy - size * 0.16f, cx + size * 0.24f, cy + size * 0.16f, paint)
                canvas.drawLine(cx + size * 0.24f, cy - size * 0.16f, cx + size * 0.02f, cy + size * 0.16f, paint)
            }
            KeyIcon.ENTER -> {
                path.reset()
                path.moveTo(cx + size * 0.4f, cy - size * 0.34f)
                path.lineTo(cx + size * 0.4f, cy + size * 0.2f)
                path.lineTo(cx - size * 0.32f, cy + size * 0.2f)
                canvas.drawPath(path, paint)
                path.reset()
                path.moveTo(cx - size * 0.02f, cy - size * 0.1f)
                path.lineTo(cx - size * 0.36f, cy + size * 0.2f)
                path.lineTo(cx - size * 0.02f, cy + size * 0.5f)
                canvas.drawPath(path, paint)
            }
            KeyIcon.MIC -> {
                val capsule = RectF(
                    cx - size * 0.18f,
                    cy - size * 0.46f,
                    cx + size * 0.18f,
                    cy + size * 0.12f,
                )
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(capsule, size * 0.18f, size * 0.18f, paint)
                paint.style = Paint.Style.STROKE
                val arc = RectF(
                    cx - size * 0.38f,
                    cy - size * 0.18f,
                    cx + size * 0.38f,
                    cy + size * 0.34f,
                )
                canvas.drawArc(arc, 0f, 180f, false, paint)
                canvas.drawLine(cx, cy + size * 0.34f, cx, cy + size * 0.52f, paint)
                canvas.drawLine(cx - size * 0.22f, cy + size * 0.52f, cx + size * 0.22f, cy + size * 0.52f, paint)
            }
            KeyIcon.GEAR -> {
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(cx, cy, size * 0.34f, paint)
                for (i in 0 until 8) {
                    val angle = i * Math.PI / 4.0
                    val x1 = cx + (size * 0.4f * kotlin.math.cos(angle)).toFloat()
                    val y1 = cy + (size * 0.4f * kotlin.math.sin(angle)).toFloat()
                    val x2 = cx + (size * 0.56f * kotlin.math.cos(angle)).toFloat()
                    val y2 = cy + (size * 0.56f * kotlin.math.sin(angle)).toFloat()
                    canvas.drawLine(x1, y1, x2, y2, paint)
                }
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, size * 0.12f, paint)
            }
            KeyIcon.CLIP -> {
                val board = RectF(cx - size * 0.3f, cy - size * 0.36f, cx + size * 0.3f, cy + size * 0.42f)
                paint.style = Paint.Style.STROKE
                canvas.drawRoundRect(board, size * 0.1f, size * 0.1f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(
                    RectF(cx - size * 0.14f, cy - size * 0.46f, cx + size * 0.14f, cy - size * 0.3f),
                    size * 0.06f,
                    size * 0.06f,
                    paint,
                )
                paint.style = Paint.Style.STROKE
                canvas.drawLine(cx - size * 0.14f, cy - size * 0.02f, cx + size * 0.14f, cy - size * 0.02f, paint)
                canvas.drawLine(cx - size * 0.14f, cy + size * 0.16f, cx + size * 0.08f, cy + size * 0.16f, paint)
            }
            KeyIcon.EMOJI -> {
                canvas.drawCircle(cx, cy, size * 0.46f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx - size * 0.16f, cy - size * 0.12f, size * 0.06f, paint)
                canvas.drawCircle(cx + size * 0.16f, cy - size * 0.12f, size * 0.06f, paint)
                paint.style = Paint.Style.STROKE
                val smile = RectF(cx - size * 0.22f, cy - size * 0.04f, cx + size * 0.22f, cy + size * 0.3f)
                canvas.drawArc(smile, 10f, 160f, false, paint)
            }
        }
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
    }

    private fun topBarTarget(x: Float, y: Float): String? {
        val slop = 8f * resources.displayMetrics.density
        val candidates = listOf(
            "mic" to micRect,
            "gear" to gearRect,
            "clip" to clipRect,
        )
        var best: String? = null
        var bestDistance = Float.MAX_VALUE
        for ((name, rect) in candidates) {
            val expanded = RectF(rect)
            expanded.inset(-slop, -slop)
            if (!expanded.contains(x, y)) continue
            val dx = x - rect.centerX()
            val dy = y - rect.centerY()
            val distance = hypot(dx, dy)
            if (distance < bestDistance) {
                bestDistance = distance
                best = name
            }
        }
        return best
    }

    private val alternatesByKey: Map<String, List<String>> = mapOf(
        "a" to listOf("á", "à", "â", "ä", "ã", "å"),
        "e" to listOf("é", "è", "ê", "ë", "ē"),
        "i" to listOf("í", "ì", "î", "ï", "ī"),
        "o" to listOf("ó", "ò", "ô", "ö", "õ", "ø"),
        "u" to listOf("ú", "ù", "û", "ü", "ū"),
        "c" to listOf("ç", "ć", "č"),
        "n" to listOf("ñ", "ń"),
        "s" to listOf("ß", "ś", "š"),
        "y" to listOf("ý", "ÿ"),
        "z" to listOf("ź", "ž", "ż"),
        "l" to listOf("ł", "ĺ"),
        "d" to listOf("ď", "đ"),
        "t" to listOf("ť", "þ"),
        "," to listOf(";", ":", "…"),
        "." to listOf("!", "?", "…"),
        "'" to listOf("’", "‘", "“", "”"),
        "-" to listOf("–", "—", "·"),
        "1" to listOf("!", "¡"),
        "2" to listOf("@", "º"),
        "3" to listOf("#", "£"),
        "4" to listOf("$", "¢"),
        "5" to listOf("%", "‰"),
        "6" to listOf("^", "&"),
        "7" to listOf("&", "¶"),
        "8" to listOf("*", "•"),
        "9" to listOf("(", "["),
        "0" to listOf(")", "]"),
    )

    private fun alternatesFor(key: Key): List<String> {
        if (key.action != KeyAction.CHARACTER || page == KeyboardPage.EMOJI) return emptyList()
        val values = alternatesByKey[key.output] ?: return emptyList()
        val upper = page == KeyboardPage.LETTERS && shift != ShiftState.OFF &&
            key.output.length == 1 && key.output[0].isLetter()
        return if (upper) values.map { if (it == "ß") "ẞ" else it.uppercase() } else values
    }

    private fun cancelPendingLongPress() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun scheduleLongPress(key: Key, rect: RectF) {
        cancelPendingLongPress()
        val options = alternatesFor(key)
        val runnable = when {
            key.action == KeyAction.SPACE -> Runnable { beginCursorMode() }
            options.isNotEmpty() -> Runnable { openAlternatePopup(key, rect, options) }
            else -> null
        } ?: return
        longPressRunnable = runnable
        handler.postDelayed(runnable, 400L)
    }

    private fun beginCursorMode() {
        if (pressedKey?.action != KeyAction.SPACE || voiceMode) return
        cursorMode = true
        longPressFired = true
        cursorAnchorX = lastDownX
        longFeedback()
        invalidate()
    }

    private fun openAlternatePopup(key: Key, rect: RectF, options: List<String>) {
        if (pressedKey?.stableId != key.stableId || options.isEmpty()) return
        alternateKey = key
        alternatePopup = options
        alternateSelected = -1
        layoutPopup(rect, options.size)
        longPressFired = true
        longFeedback()
        invalidate()
    }

    private fun layoutPopup(keyRect: RectF, count: Int) {
        val density = resources.displayMetrics.density
        val cellW = 34f * density
        val cellH = 44f * density
        val gap = 4f * density
        val pad = 5f * density
        val w = count * cellW + (count - 1) * gap + 2f * pad
        val h = cellH + 2f * pad
        val left = (keyRect.centerX() - w / 2f).coerceIn(8f * density, max(8f * density, width - w - 8f * density))
        var top = keyRect.top - h - 6f * density
        if (top < 2f * density) top = keyRect.bottom + 6f * density
        top = top.coerceAtMost(max(2f * density, height - h - 2f * density))
        popupBounds.set(left, top, left + w, top + h)
        popupCells.clear()
        for (i in 0 until count) {
            val x = left + pad + i * (cellW + gap)
            popupCells.add(RectF(x, top + pad, x + cellW, top + pad + cellH))
        }
    }

    private fun updateAlternateSelection(x: Float, y: Float) {
        if (popupCells.isEmpty()) return
        if (y < popupBounds.top || y > popupBounds.bottom) {
            setAlternateSelected(-1)
            return
        }
        val density = resources.displayMetrics.density
        var best = 0
        var bestDistance = Float.MAX_VALUE
        for (i in popupCells.indices) {
            val cell = popupCells[i]
            val distance = when {
                x < cell.left -> cell.left - x
                x > cell.right -> x - cell.right
                else -> 0f
            }
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        setAlternateSelected(if (bestDistance > 26f * density) -1 else best)
    }

    private fun setAlternateSelected(index: Int) {
        if (alternateSelected == index) return
        alternateSelected = index
        if (index >= 0) tickFeedback()
        invalidate()
    }

    private fun commitAlternate(x: Float, y: Float) {
        val options = alternatePopup
        val index = alternateSelected
        val key = alternateKey
        val anchor = pressedRect
        if (options != null && index in options.indices) {
            listener?.onAlternate(options[index])
            if (shift == ShiftState.ON) {
                shift = ShiftState.OFF
            }
        } else if (key != null && anchor != null && anchor.contains(x, y)) {
            // A stationary long-press release on the key keeps the base character.
            performKey(key)
        }
        pressedKey = null
        pressedRect = null
        pressedPointerId = -1
        clearModalGesture()
        invalidate()
    }

    private fun clearModalGesture() {
        cancelPendingLongPress()
        alternatePopup = null
        alternateSelected = -1
        alternateKey = null
        popupCells.clear()
        popupBounds.setEmpty()
        cursorMode = false
        backspaceWordDeleted = false
        pressStartedOnBackspace = false
        longPressFired = false
    }

    private fun handleCursorMove(x: Float) {
        val step = 16f * resources.displayMetrics.density
        var moved = false
        while (abs(x - cursorAnchorX) >= step) {
            val direction = if (x > cursorAnchorX) 1 else -1
            listener?.onCursorMove(direction)
            cursorAnchorX += direction * step
            moved = true
        }
        if (moved) tickFeedback()
    }

    private fun tapFeedback() {
        if (hapticsEnabled) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            hapticEvents++
        }
        if (soundEnabled) playKeySound()
    }

    private fun longFeedback() {
        if (hapticsEnabled) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            hapticEvents++
        }
        if (soundEnabled) playKeySound()
    }

    private fun tickFeedback() {
        if (hapticsEnabled) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            hapticEvents++
        }
    }

    private fun playKeySound() {
        try {
            context.getSystemService(AudioManager::class.java)
                ?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.65f)
            soundEvents++
        } catch (_: Throwable) {
            // Sound effects are best-effort; a silent device must never crash typing.
        }
    }

    private fun drawAlternatePopup(canvas: Canvas) {
        val options = alternatePopup ?: return
        if (popupCells.isEmpty()) return
        val density = resources.displayMetrics.density
        val radius = 11f * density
        paint.style = Paint.Style.FILL
        paint.color = palette.shadow
        canvas.drawRoundRect(
            popupBounds.left,
            popupBounds.top + 3f * density,
            popupBounds.right,
            popupBounds.bottom + 3f * density,
            radius,
            radius,
            paint,
        )
        paint.color = palette.suggestionBg
        canvas.drawRoundRect(popupBounds, radius, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * density
        paint.color = palette.border
        canvas.drawRoundRect(popupBounds, radius, radius, paint)
        paint.style = Paint.Style.FILL
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 21f * density
        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textPaint.isFakeBoldText = false
        for (i in popupCells.indices) {
            val cell = popupCells[i]
            if (i == alternateSelected) {
                paint.color = palette.accent
                canvas.drawRoundRect(cell, 8f * density, 8f * density, paint)
            }
            textPaint.color = if (i == alternateSelected) palette.onAccent else palette.text
            val label = options.getOrElse(i) { "" }
            canvas.drawText(
                label,
                cell.centerX(),
                cell.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f,
                textPaint,
            )
        }
    }

    private fun drawCursorHint(canvas: Canvas) {
        val spaceRect = keyRects.firstOrNull { it.first.action == KeyAction.SPACE }?.second ?: return
        val density = resources.displayMetrics.density
        val label = "←  slide to move cursor  →"
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 11f * density
        textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textPaint.isFakeBoldText = false
        val widthPx = textPaint.measureText(label) + 28f * density
        val heightPx = 30f * density
        val left = (spaceRect.centerX() - widthPx / 2f).coerceIn(8f * density, max(8f * density, width - widthPx - 8f * density))
        var top = spaceRect.top - heightPx - 8f * density
        if (top < 2f * density) top = spaceRect.bottom + 6f * density
        val rect = RectF(left, top, left + widthPx, top + heightPx)
        paint.style = Paint.Style.FILL
        paint.color = palette.accent
        canvas.drawRoundRect(rect, heightPx / 2f, heightPx / 2f, paint)
        textPaint.color = palette.onAccent
        canvas.drawText(label, rect.centerX(), rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        val x = event.x
        val y = event.y
        if (voiceMode) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    activePointerId = event.getPointerId(0)
                    if (cancelRect.contains(x, y)) {
                        listener?.onCancelDictation()
                        return true
                    }
                    val dx = x - voiceMicCenter.x
                    val dy = y - voiceMicCenter.y
                    val radius = dimens.voiceMicRadius * resources.displayMetrics.density * 1.4f
                    if (hypot(dx, dy) <= radius) {
                        tapFeedback()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    activePointerId = -1
                    if (cancelRect.contains(x, y)) return true
                    val dx = x - voiceMicCenter.x
                    val dy = y - voiceMicCenter.y
                    val radius = dimens.voiceMicRadius * resources.displayMetrics.density * 1.4f
                    if (hypot(dx, dy) <= radius) {
                        listener?.onMic()
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    activePointerId = -1
                    return true
                }
                else -> return true
            }
        }

        if (clipboardMode) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    activePointerId = event.getPointerId(0)
                    if (clipboardRects.values.any { it.contains(x, y) }) {
                        tapFeedback()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    activePointerId = -1
                    val target = clipboardRects.entries.firstOrNull { it.value.contains(x, y) }?.key
                    when {
                        target == "close" -> listener?.onClipboardClose()
                        target == "paste" -> listener?.onClipboardPaste()
                        target == "copy" -> listener?.onClipboardCopy()
                        target == "selectAll" -> listener?.onClipboardSelectAll()
                        target == "clear" -> listener?.onClipboardClear()
                        target != null && target.startsWith("item") -> {
                            val index = target.removePrefix("item").toIntOrNull()
                            if (index != null) clipboardItems.getOrNull(index)?.let { listener?.onClipboardItem(it) }
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    activePointerId = -1
                    return true
                }
                else -> return true
            }
        }

        if (alternatePopup != null || cursorMode) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (cursorMode) handleCursorMove(x) else updateAlternateSelection(x, y)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (cursorMode) {
                        clearModalGesture()
                        pressedKey = null
                        pressedRect = null
                        pressedPointerId = -1
                        invalidate()
                    } else {
                        commitAlternate(x, y)
                    }
                    activePointerId = -1
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    activePointerId = -1
                    clearModalGesture()
                    pressedKey = null
                    pressedRect = null
                    pressedPointerId = -1
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> return true
            }
        }

        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            val index = event.actionIndex
            val id = event.getPointerId(index)
            val hit = hitTest(event.getX(index), event.getY(index))
            if (hit != null) {
                secondaryPresses[id] = hit
                tapFeedback()
            }
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) {
            val index = event.actionIndex
            val id = event.getPointerId(index)
            val secondary = secondaryPresses.remove(id)
            if (secondary != null) {
                val hit = hitTest(event.getX(index), event.getY(index))
                if (hit != null) performKey(hit.first)
            } else if (id == pressedPointerId) {
                finishPress(event.getX(index), event.getY(index))
            }
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                pressedPointerId = event.getPointerId(0)
                longPressFired = false
                backspaceWordDeleted = false
                pressStartedOnBackspace = false
                lastDownX = x
                lastDownY = y
                val topTarget = topBarTarget(x, y)
                when {
                    topTarget != null -> {
                        pressedTopBar = topTarget
                        tapFeedback()
                    }
                    suggestionRects.indexOfFirst {
                        RectF(it).apply { inset(-4f * resources.displayMetrics.density, -4f * resources.displayMetrics.density) }.contains(x, y)
                    } >= 0 -> {
                        val index = suggestionRects.indexOfFirst {
                            RectF(it).apply { inset(-4f * resources.displayMetrics.density, -4f * resources.displayMetrics.density) }.contains(x, y)
                        }
                        pressedTopBar = "suggestion"
                        pressedSuggestionText = suggestions.getOrNull(index)
                        pressedSuggestionRect = suggestionRects.getOrNull(index)
                        tapFeedback()
                    }
                    undoAvailable && undoRect.contains(x, y) && suggestionRect.contains(x, y) -> {
                        pressedTopBar = "undo"
                        tapFeedback()
                    }
                    else -> {
                        val hit = hitTest(x, y)
                        if (hit != null) {
                            pressedKey = hit.first
                            pressedRect = hit.second
                            pressStartedOnBackspace = hit.first.action == KeyAction.BACKSPACE
                            tapFeedback()
                            if (hit.first.action == KeyAction.BACKSPACE) {
                                handler.postDelayed({
                                    if (pressedKey?.action == KeyAction.BACKSPACE) {
                                        longPressFired = true
                                        longFeedback()
                                        listener?.onBackspace()
                                        handler.postDelayed(repeatRunnable, 260L)
                                    }
                                }, 400L)
                            } else {
                                scheduleLongPress(hit.first, hit.second)
                            }
                            invalidate()
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val travelX = x - lastDownX
                val travelY = y - lastDownY
                if (!backspaceWordDeleted && pressStartedOnBackspace &&
                    travelX < -56f * resources.displayMetrics.density &&
                    abs(travelX) > 3f * abs(travelY)
                ) {
                    backspaceWordDeleted = true
                    longPressFired = true
                    handler.removeCallbacksAndMessages(null)
                    longFeedback()
                    listener?.onDeleteWord()
                    invalidate()
                    return true
                }
                if (hypot(travelX, travelY) > 8f * resources.displayMetrics.density) {
                    cancelPendingLongPress()
                }
                val ids = ArrayList<Int>()
                if (pressedPointerId >= 0) ids.add(pressedPointerId)
                ids.addAll(secondaryPresses.keys)
                for (id in ids) {
                    val index = event.findPointerIndex(id)
                    if (index < 0) continue
                    val hit = hitTest(event.getX(index), event.getY(index))
                    if (id == pressedPointerId) {
                        if (hit != null && pressedKey?.stableId != hit.first.stableId) {
                            if (pressedKey?.action == KeyAction.BACKSPACE) handler.removeCallbacksAndMessages(null)
                            pressedKey = hit.first
                            pressedRect = hit.second
                            invalidate()
                        }
                    } else if (hit != null) {
                        secondaryPresses[id] = hit
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                activePointerId = -1
                cancelPendingLongPress()
                val id = event.getPointerId(0)
                val secondary = secondaryPresses.remove(id)
                if (secondary != null) {
                    val hit = hitTest(x, y)
                    if (hit != null) performKey(hit.first)
                }
                if (pressedPointerId != -1) finishPress(x, y)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                activePointerId = -1
                handler.removeCallbacksAndMessages(null)
                clearModalGesture()
                pressedKey = null
                pressedRect = null
                pressedTopBar = null
                pressedPointerId = -1
                secondaryPresses.clear()
                pressedSuggestionText = null
                pressedSuggestionRect = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun finishPress(x: Float, y: Float) {
        handler.removeCallbacksAndMessages(null)
        cancelPendingLongPress()
        if (alternatePopup != null || cursorMode) {
            clearModalGesture()
            pressedKey = null
            pressedRect = null
            pressedPointerId = -1
            invalidate()
            return
        }
        val top = pressedTopBar
        pressedTopBar = null
        if (top != null) {
            when {
                top == "mic" -> if (topBarTarget(x, y) == "mic") listener?.onMic()
                top == "gear" -> if (topBarTarget(x, y) == "gear") listener?.onSettings()
                top == "clip" -> if (topBarTarget(x, y) == "clip") listener?.onClipboardRequest()
                top == "undo" -> if (undoRect.contains(x, y)) listener?.onUndo()
                top == "suggestion" -> {
                    val rect = pressedSuggestionRect
                    val expanded = rect?.let { RectF(it).apply { inset(-4f * resources.displayMetrics.density, -4f * resources.displayMetrics.density) } }
                    val text = pressedSuggestionText
                    if (text != null && expanded != null && expanded.contains(x, y)) listener?.onSuggestion(text)
                }
            }
            pressedPointerId = -1
            pressedSuggestionText = null
            pressedSuggestionRect = null
            return
        }
        val downKey = pressedKey
        val rect = pressedRect
        pressedKey = null
        pressedRect = null
        pressedPointerId = -1
        pressedSuggestionText = null
        pressedSuggestionRect = null
        invalidate()
        val releaseKey = hitTest(x, y)?.first ?: downKey
        if (releaseKey != null && !longPressFired) {
            performClick()
            performKey(releaseKey)
        }
        if (rect == null && downKey == null) return
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun hitTest(x: Float, y: Float): Pair<Key, RectF>? {
        for ((key, touchRect) in keyTouchRects) {
            if (touchRect.contains(x, y)) {
                return key to (keyById[key.stableId] ?: touchRect)
            }
        }
        return null
    }

    private fun performKey(key: Key) {
        if (key.action != KeyAction.SHIFT) lastShiftTapTime = 0L
        when (key.action) {
            KeyAction.CHARACTER -> {
                var text = key.output
                if (page == KeyboardPage.LETTERS && shift != ShiftState.OFF) {
                    text = text.uppercase()
                }
                listener?.onText(text)
                if (shift == ShiftState.ON) {
                    shift = ShiftState.OFF
                    invalidate()
                }
            }
            KeyAction.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                val doubleTap = now - lastShiftTapTime < 350L
                shift = when {
                    shift == ShiftState.CAPS -> ShiftState.OFF
                    doubleTap -> ShiftState.CAPS
                    shift == ShiftState.ON -> ShiftState.OFF
                    else -> ShiftState.ON
                }
                lastShiftTapTime = now
                invalidate()
            }
            KeyAction.BACKSPACE -> listener?.onBackspace()
            KeyAction.ENTER -> listener?.onEnter()
            KeyAction.SPACE -> listener?.onSpace()
            KeyAction.SYMBOLS -> setPage(if (page == KeyboardPage.SYMBOLS) KeyboardPage.MORE_SYMBOLS else KeyboardPage.SYMBOLS)
            KeyAction.LETTERS -> setPage(KeyboardPage.LETTERS)
            KeyAction.EMOJI -> setPage(KeyboardPage.EMOJI)
            KeyAction.MIC -> listener?.onMic()
            KeyAction.SETTINGS -> listener?.onSettings()
            KeyAction.NONE -> Unit
        }
    }

    private fun cancelVoiceMode() {
        voiceMode = false
        recording = false
        processing = false
        liveText = ""
        invalidate()
    }

    fun debugKeyMap(): String {
        layoutGeometry(width, height)
        val sb = StringBuilder()
        sb.append("{\"width\":").append(width).append(",\"height\":").append(height)
        sb.append(",\"topBarMic\":").append(rectJson(micRect))
        sb.append(",\"gear\":").append(rectJson(gearRect))
        sb.append(",\"clip\":").append(rectJson(clipRect))
        sb.append(",\"suggestions\":[")
        for (i in suggestionRects.indices) {
            if (i > 0) sb.append(',')
            sb.append("{\"label\":\"").append(escape(suggestions.getOrElse(i) { "" })).append("\",")
            sb.append("\"x\":").append(suggestionRects[i].left.toInt()).append(",\"y\":").append(suggestionRects[i].top.toInt())
                .append(",\"w\":").append(suggestionRects[i].width().toInt()).append(",\"h\":").append(suggestionRects[i].height().toInt()).append('}')
        }
        sb.append(']')
        if (clipboardMode) {
            sb.append(",\"clipboard\":{")
            var firstClip = true
            for ((name, rect) in clipboardRects) {
                if (!firstClip) sb.append(',')
                firstClip = false
                sb.append('"').append(name).append("\":").append(rectJson(rect))
            }
            sb.append('}')
        }
        sb.append(",\"suggestion\":").append(rectJson(suggestionRect))
        sb.append(",\"undo\":").append(rectJson(undoRect))
        sb.append(",\"cancel\":").append(rectJson(cancelRect))
        sb.append(",\"voiceMic\":{\"x\":").append((voiceMicCenter.x - dimens.voiceMicRadius * resources.displayMetrics.density).toInt())
            .append(",\"y\":").append((voiceMicCenter.y - dimens.voiceMicRadius * resources.displayMetrics.density).toInt())
            .append(",\"w\":").append((dimens.voiceMicRadius * resources.displayMetrics.density * 2f).toInt())
            .append(",\"h\":").append((dimens.voiceMicRadius * resources.displayMetrics.density * 2f).toInt()).append('}')
        sb.append(",\"keys\":[")
        var first = true
        for ((key, rect) in keyRects) {
            if (!first) sb.append(',')
            first = false
            sb.append("{\"id\":\"").append(escape(key.stableId)).append("\",\"label\":\"")
                .append(escape(KeyboardLayouts.displayLabel(key, page, shift))).append("\",\"action\":\"")
                .append(key.action.name).append("\",\"x\":").append(rect.left.toInt())
                .append(",\"y\":").append(rect.top.toInt())
                .append(",\"w\":").append(rect.width().toInt())
                .append(",\"h\":").append(rect.height().toInt())
                .append(",\"alternates\":[").append(alternatesFor(key).joinToString(",") { "\"${escape(it)}\"" }).append("]")
                .append('}')
        }
        sb.append("]")
        sb.append(",\"popup\":")
        if (alternatePopup == null) {
            sb.append("null")
        } else {
            sb.append("{\"selected\":").append(alternateSelected)
            sb.append(",\"bounds\":").append(rectJson(popupBounds))
            sb.append(",\"cells\":[")
            for (i in popupCells.indices) {
                if (i > 0) sb.append(',')
                val label = alternatePopup?.getOrElse(i) { "" } ?: ""
                sb.append("{\"label\":\"").append(escape(label)).append("\",")
                sb.append(rectJson(popupCells[i]).trimStart('{'))
            }
            sb.append("]}")
        }
        sb.append('}')
        return sb.toString()
    }

    fun debugState(): String {
        return "{\"voiceMode\":$voiceMode,\"recording\":$recording,\"processing\":$processing," +
            "\"clipboardMode\":$clipboardMode,\"suggestions\":${suggestions.size},\"micEnabled\":$micEnabled,\"page\":\"${page.name}\",\"shift\":\"${shift.name}\"," +
            "\"width\":$width,\"height\":$height,\"bottomInset\":$bottomInset,\"numberRow\":$numberRow," +
            "\"alternatePopup\":${alternatePopup != null},\"alternateSelected\":$alternateSelected,\"cursorMode\":$cursorMode," +
            "\"viewAccent\":\"#${String.format("%06X", 0xFFFFFF and palette.accent)}\"," +
            "\"viewBackground\":\"#${String.format("%06X", 0xFFFFFF and palette.background)}\"," +
            "\"viewNumberRow\":$numberRow,\"status\":\"${escape(statusText)}\"}"
    }

    fun keyRectFor(label: String): RectF? {
        layoutGeometry(width, height)
        for ((key, rect) in keyRects) {
            if (KeyboardLayouts.displayLabel(key, page, shift) == label) return rect
        }
        return null
    }

    fun micRectForDebug(): RectF = RectF(micRect)

    private fun rectJson(rect: RectF): String =
        "{\"x\":${rect.left.toInt()},\"y\":${rect.top.toInt()},\"w\":${rect.width().toInt()},\"h\":${rect.height().toInt()}}"

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }
}
