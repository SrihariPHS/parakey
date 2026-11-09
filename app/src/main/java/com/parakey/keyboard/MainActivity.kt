package com.parakey.keyboard

import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.parakey.keyboard.ui.colorWithAlpha
import com.parakey.keyboard.ui.dp
import com.parakey.keyboard.ui.roundedDrawable
import com.parakey.keyboard.ui.setRoundedBackground
import com.parakey.keyboard.ui.styleButton
import com.parakey.keyboard.voice.ModelDownloader
import com.parakey.keyboard.voice.ModelRepository
import com.parakey.keyboard.voice.ParakeetEngine

class MainActivity : android.app.Activity() {

    private data class AppColors(
        val background: Int,
        val card: Int,
        val text: Int,
        val muted: Int,
        val border: Int,
        val accent: Int,
        val onAccent: Int,
        val danger: Int,
        val warning: Int,
        val headerStart: Int,
        val headerEnd: Int,
    )

    private data class StepRefs(
        val status: TextView,
        val primary: TextView,
        val secondary: TextView?,
        val note: TextView?,
        val progress: ProgressBar?,
    )

    private lateinit var settings: SettingsStore
    private lateinit var repository: ModelRepository
    private lateinit var colors: AppColors
    private lateinit var downloader: ModelDownloader
    private var night = false
    private var downloading = false

    private lateinit var stepEnable: StepRefs
    private lateinit var stepModel: StepRefs
    private lateinit var stepMic: StepRefs
    private lateinit var tryField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = SettingsStore(this)
        intent?.getStringExtra("theme")?.let { settings.theme = it }
        night = when (settings.theme) {
            "dark" -> true
            "light" -> false
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
        setTheme(if (night) R.style.Theme_Parakey_Dark else R.style.Theme_Parakey)
        super.onCreate(savedInstanceState)
        colors = appColors(night)
        repository = ModelRepository(this)
        downloader = ModelDownloader(this)
        buildUi()
        configureWindow()
        refreshSetup()
        if (!settings.hasCompletedOnboarding) {
            settings.hasCompletedOnboarding = true
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val next = intent?.getStringExtra("theme") ?: return
        if (settings.theme != next) {
            settings.theme = next
            recreate()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSetup()
    }

    override fun onDestroy() {
        downloader.cancel()
        super.onDestroy()
    }

    private fun configureWindow() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.decorView.post {
                val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                window.insetsController?.setSystemBarsAppearance(if (night) 0 else mask, mask)
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                (if (night) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }

    private fun appColors(dark: Boolean) = if (dark) {
        AppColors(
            background = Color.rgb(15, 23, 42),
            card = Color.rgb(30, 41, 59),
            text = Color.rgb(241, 245, 249),
            muted = Color.rgb(148, 163, 184),
            border = Color.rgb(51, 65, 85),
            accent = Color.rgb(45, 212, 191),
            onAccent = Color.rgb(4, 47, 46),
            danger = Color.rgb(251, 113, 133),
            warning = Color.rgb(251, 191, 36),
            headerStart = Color.rgb(8, 16, 30),
            headerEnd = Color.rgb(15, 118, 110),
        )
    } else {
        AppColors(
            background = Color.rgb(246, 248, 251),
            card = Color.WHITE,
            text = Color.rgb(15, 23, 42),
            muted = Color.rgb(100, 116, 139),
            border = Color.rgb(226, 232, 240),
            accent = Color.rgb(18, 184, 134),
            onAccent = Color.WHITE,
            danger = Color.rgb(225, 29, 72),
            warning = Color.rgb(217, 119, 6),
            headerStart = Color.rgb(11, 18, 32),
            headerEnd = Color.rgb(15, 118, 110),
        )
    }

    private fun buildUi() {
        val root = FrameLayout(this)
        root.setBackgroundColor(colors.background)
        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(28))
        }
        scroll.addView(content, LinearLayout.LayoutParams(-1, -2))
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(0, bars.top, 0, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            }
            insets
        }

        content.addView(buildHeader())
        content.addView(sectionLabel("SET UP"))
        stepEnable = addStepCard(
            content,
            number = 1,
            title = "Enable the keyboard",
            description = "Parakey is a real Android keyboard. Turn it on, then pick it as your keyboard.",
            primaryText = "Enable keyboard",
            primaryAction = { openKeyboardSettings() },
            secondaryText = "Choose keyboard",
            secondaryAction = { showKeyboardPicker() },
        )
        val modelExtra = buildModelExtra()
        stepModel = addStepCard(
            content,
            number = 2,
            title = "Add the speech model",
            description = "Parakeet TDT-CTC 110M runs offline on this phone. One download, no audio leaves the device.",
            primaryText = "Download model",
            primaryAction = { onModelAction() },
            secondaryText = "Re-download",
            secondaryAction = { reinstallModel() },
            extra = modelExtra.first,
        )
        stepMic = addStepCard(
            content,
            number = 3,
            title = "Allow the microphone",
            description = "Only the mic key uses the microphone. Voice typing turns itself off in password fields.",
            primaryText = "Allow microphone",
            primaryAction = { onMicrophoneAction() },
        )

        content.addView(sectionLabel("TRY IT"))
        content.addView(buildTryCard())
        content.addView(sectionLabel("APPEARANCE"))
        content.addView(buildThemeCard())
        content.addView(buildFooter())
    }

    private fun buildHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(colors.headerStart, colors.headerEnd),
            ).apply { cornerRadius = dp(24).toFloat() }
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val iconWrap = FrameLayout(this).apply {
            setRoundedBackground(colorWithAlpha(Color.WHITE, 22), 16f)
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        iconWrap.addView(icon, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
        top.addView(iconWrap, LinearLayout.LayoutParams(dp(58), dp(58)))
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        titles.addView(appText("Parakey", 28f, Color.WHITE, bold = true))
        titles.addView(appText("Type. Talk. Done.", 13f, Color.rgb(196, 220, 216)))
        top.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(top)

        val badges = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, 0)
        }
        badges.addView(badge("100% ON-DEVICE", Color.rgb(167, 243, 208), Color.rgb(6, 78, 59)))
        badges.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        badges.addView(badge("PARAKEET 110M", Color.WHITE, Color.rgb(15, 118, 110)))
        header.addView(badges)

        val miniRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, 0)
        }
        for (letter in listOf("Q", "W", "E", "R")) {
            val key = appText(letter, 12f, Color.WHITE, bold = true)
            key.gravity = Gravity.CENTER
            key.setRoundedBackground(colorWithAlpha(Color.WHITE, 20), 8f)
            miniRow.addView(key, LinearLayout.LayoutParams(0, dp(32), 1f).apply {
                marginEnd = dp(4)
            })
        }
        val micWrap = FrameLayout(this).apply {
            setRoundedBackground(colors.accent, 9f)
        }
        val mic = ImageView(this).apply {
            setImageResource(R.drawable.ic_mic)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        micWrap.addView(mic, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER))
        miniRow.addView(micWrap, LinearLayout.LayoutParams(dp(38), dp(32)).apply { marginStart = dp(2) })
        header.addView(miniRow)

        return header.apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }
        }
    }

    private fun badge(text: String, textColor: Int, bg: Int): TextView {
        return appText(text, 9.5f, textColor, bold = true).apply {
            letterSpacing = 0.08f
            setPadding(dp(10), dp(5), dp(10), dp(5))
            setRoundedBackground(colorWithAlpha(bg, 70), 50f)
        }
    }

    private fun sectionLabel(text: String): TextView {
        return appText(text, 10.5f, colors.muted, bold = true).apply {
            letterSpacing = 0.14f
            setPadding(dp(4), dp(18), dp(4), dp(8))
        }
    }

    private fun addStepCard(
        parent: LinearLayout,
        number: Int,
        title: String,
        description: String,
        primaryText: String,
        primaryAction: () -> Unit,
        secondaryText: String? = null,
        secondaryAction: (() -> Unit)? = null,
        extra: View? = null,
    ): StepRefs {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(14))
            background = roundedDrawable(this@MainActivity, colors.card, 18f, colors.border, 1f)
            elevation = dp(1).toFloat()
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val numberView = appText(number.toString(), 12f, colors.accent, bold = true).apply {
            gravity = Gravity.CENTER
            setRoundedBackground(colorWithAlpha(colors.accent, 28), 50f)
        }
        titleRow.addView(numberView, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(10) })
        titleRow.addView(appText(title, 16f, colors.text, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        val status = appText("CHECKING", 9f, colors.muted, bold = true).apply {
            letterSpacing = 0.06f
            setPadding(dp(9), dp(4), dp(9), dp(4))
            setRoundedBackground(colorWithAlpha(colors.muted, 22), 50f)
        }
        titleRow.addView(status, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        card.addView(titleRow)
        card.addView(appText(description, 12.5f, colors.muted).apply {
            setPadding(0, dp(9), 0, 0)
            setLineSpacing(dp(2).toFloat(), 1f)
        })

        val note: TextView?
        val progress: ProgressBar?
        if (extra is LinearLayout && extra.tag == "model_extra") {
            progress = extra.getChildAt(0) as ProgressBar
            note = extra.getChildAt(1) as TextView
            card.addView(extra)
        } else {
            progress = null
            note = null
            if (extra != null) card.addView(extra)
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(13), 0, 0)
        }
        val primary = appText(primaryText, 13f, colors.onAccent, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setRoundedBackground(colors.accent, 12f, rippleColor = colorWithAlpha(colors.onAccent, 35))
            isClickable = true
            isFocusable = true
            setOnClickListener { primaryAction() }
        }
        actions.addView(primary, LinearLayout.LayoutParams(-2, -2))
        val secondary = if (secondaryText != null) {
            appText(secondaryText, 12f, colors.accent, bold = true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(10), dp(4), dp(10))
                isClickable = true
                isFocusable = true
                setOnClickListener { secondaryAction?.invoke() }
            }
        } else null
        if (secondary != null) {
            actions.addView(secondary, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
        }
        card.addView(actions)

        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        return StepRefs(status, primary, secondary, note, progress)
    }

    private fun buildModelExtra(): Pair<View, TextView> {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "model_extra"
            setPadding(0, dp(10), 0, 0)
        }
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(colors.accent)
            visibility = View.GONE
        }
        val note = appText("One-time 126 MB download · CC-BY-4.0 · works offline", 10.5f, colors.muted)
        box.addView(progress, LinearLayout.LayoutParams(-1, dp(4)).apply { bottomMargin = dp(7) })
        box.addView(note)
        return box to note
    }

    private fun buildTryCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = roundedDrawable(this@MainActivity, colors.card, 18f, colors.border, 1f)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(appText("Try the keyboard", 16f, colors.text, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(badge("LIVE", colors.accent, colors.accent))
        card.addView(row)
        card.addView(appText(
            "Tap the field, then tap the mint mic on the keyboard and speak.",
            12.5f,
            colors.muted,
        ).apply { setPadding(0, dp(5), 0, dp(11)) })
        tryField = EditText(this).apply {
            id = R.id.field_home_try
            hint = "Type here, or dictate with your voice…"
            setHintTextColor(colors.muted)
            setTextColor(colors.text)
            textSize = 16f
            minLines = 3
            maxLines = 5
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(13), dp(12), dp(13), dp(12))
            setRoundedBackground(colors.background, 12f, strokeColor = colors.border, strokeWidthDp = 1f)
        }
        card.addView(tryField, LinearLayout.LayoutParams(-1, -2))
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        val open = appText("Open test pad", 13f, colors.onAccent, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setRoundedBackground(colors.accent, 12f, rippleColor = colorWithAlpha(colors.onAccent, 35))
            setOnClickListener {
                startActivity(Intent(this@MainActivity, TryActivity::class.java))
            }
        }
        buttons.addView(open, LinearLayout.LayoutParams(-2, -2))
        val clear = appText("Clear", 12f, colors.muted, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener {
                tryField.setText("")
            }
        }
        buttons.addView(clear, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(2) })
        card.addView(buttons)
        return card.apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        }
    }

    private fun buildThemeCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = roundedDrawable(this@MainActivity, colors.card, 18f, colors.border, 1f)
        }
        card.addView(appText("Appearance", 16f, colors.text, bold = true))
        card.addView(appText("The keyboard follows this setting.", 12f, colors.muted).apply {
            setPadding(0, dp(4), 0, dp(11))
        })
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        for ((value, label) in listOf("system" to "System", "light" to "Light", "dark" to "Dark")) {
            val selected = settings.theme == value
            val button = appText(label, 12f, if (selected) colors.onAccent else colors.muted, bold = true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(9), dp(10), dp(9))
                setRoundedBackground(
                    if (selected) colors.accent else colors.background,
                    11f,
                    rippleColor = colorWithAlpha(colors.accent, 40),
                    strokeColor = if (selected) colors.accent else colors.border,
                    strokeWidthDp = 1f,
                )
                setOnClickListener {
                    settings.theme = value
                    recreate()
                }
            }
            row.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = if (value != "dark") dp(6) else 0
            })
        }
        card.addView(row)
        return card
    }

    private fun buildFooter(): View {
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(4))
        }
        footer.addView(appText("Parakey ${BuildConfig.VERSION_NAME} · On-device speech", 10.5f, colors.muted))
        footer.addView(appText(
            "NVIDIA Parakeet TDT-CTC 110M (CC-BY-4.0) · sherpa-onnx",
            10f,
            colors.muted,
        ).apply { setPadding(0, dp(4), 0, 0) })
        val links = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }
        links.addView(link("Project on GitHub") { openUrl(BuildConfig.PROJECT_URL) })
        links.addView(appText("  ·  ", 10f, colors.muted))
        links.addView(link("Model license") { openUrl("https://creativecommons.org/licenses/by/4.0/") })
        footer.addView(links)
        return footer
    }

    private fun link(text: String, action: () -> Unit): TextView =
        appText(text, 10.5f, colors.accent, bold = true).apply {
            setOnClickListener { action() }
            isClickable = true
        }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Throwable) {
            Toast.makeText(this, "No browser available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun appText(text: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(color)
            textSize = sizeSp
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
        }

    private fun refreshSetup() {
        if (!::stepEnable.isInitialized) return
        val component = ComponentName(this, ParakeyImeService::class.java)
        val inputMethodManager = getSystemService(InputMethodManager::class.java)
        val isEnabled = inputMethodManager
            ?.enabledInputMethodList
            ?.any {
                it.packageName == component.packageName &&
                    (it.serviceName == component.className || it.serviceName == component.flattenToShortString())
            }
            ?: false
        // Android 14+ no longer lets target-35 apps read the default IME secure
        // setting. A live service instance is a reliable proxy: the system binds
        // the IME service once it is selected or used.
        val isSelected = isEnabled && ParakeyImeService.instance != null
        when {
            isSelected -> setPill(stepEnable.status, "SELECTED", colors.accent)
            isEnabled -> setPill(stepEnable.status, "ENABLED", colors.warning)
            else -> setPill(stepEnable.status, "NOT ENABLED", colors.danger)
        }
        stepEnable.primary.text = if (isSelected) "Keyboard settings" else "Enable keyboard"

        val modelReady = repository.isInstalled()
        if (downloading) {
            setPill(stepModel.status, "DOWNLOADING", colors.warning)
        } else if (modelReady) {
            setPill(stepModel.status, "READY", colors.accent)
        } else {
            setPill(stepModel.status, "NEEDED", colors.warning)
        }
        stepModel.primary.text = if (modelReady) "Model ready ✓" else "Download model"
        stepModel.primary.isEnabled = !downloading
        stepModel.secondary?.visibility = if (modelReady) View.VISIBLE else View.GONE
        if (!downloading && !modelReady) {
            stepModel.note?.text = if (BuildConfig.MODEL_URL.isBlank()) {
                "Dev build: push the model with scripts/push-model.sh"
            } else {
                "One-time 126 MB download · CC-BY-4.0 · works offline"
            }
        } else if (modelReady) {
            stepModel.note?.text = "Ready on this device · ${repository.installedBytes() / (1024 * 1024)} MB · offline"
        }

        val micGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        setPill(stepMic.status, if (micGranted) "GRANTED" else "NEEDED", if (micGranted) colors.accent else colors.warning)
        stepMic.primary.text = if (micGranted) "Microphone allowed ✓" else "Allow microphone"

        if (!isSelected) {
            tryField.hint = "Enable Parakey, then tap here to try it…"
        } else {
            tryField.hint = "Type here, or dictate with your voice…"
        }
    }

    private fun setPill(view: TextView, text: String, color: Int) {
        view.text = text
        view.setTextColor(color)
        view.background = roundedDrawable(this, colorWithAlpha(color, 24), 50f)
    }

    private fun onModelAction() {
        if (repository.isInstalled()) {
            Toast.makeText(this, "The Parakeet model is ready.", Toast.LENGTH_SHORT).show()
            ParakeetEngine.get(this).preload()
            return
        }
        if (downloading) return
        if (BuildConfig.MODEL_URL.isBlank() || BuildConfig.TOKENS_URL.isBlank()) {
            AlertDialog.Builder(this)
                .setTitle("Model URL not set")
                .setMessage(
                    "This development build does not have a hosted model URL.\n\n" +
                        "Push the model with scripts/push-model.sh, or rebuild with " +
                        "-PmodelUrl=… -PtokensUrl=… pointing at the release assets.",
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }
        startDownload()
    }

    private fun startDownload() {
        downloading = true
        stepModel.progress?.visibility = View.VISIBLE
        stepModel.progress?.progress = 0
        stepModel.note?.text = "Downloading… 0%"
        stepModel.primary.text = "Downloading…"
        stepModel.primary.isEnabled = false
        setPill(stepModel.status, "DOWNLOADING", colors.warning)
        downloader = ModelDownloader(this)
        downloader.start(object : ModelDownloader.Listener {
            override fun onProgress(percent: Int, downloadedBytes: Long, totalBytes: Long) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    downloading = true
                    stepModel.progress?.progress = percent
                    stepModel.note?.text = "Downloading… $percent% · ${downloadedBytes / (1024 * 1024)} / ${totalBytes / (1024 * 1024)} MB"
                    setPill(stepModel.status, "DOWNLOADING", colors.warning)
                }
            }

            override fun onComplete() {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    downloading = false
                    stepModel.progress?.visibility = View.GONE
                    stepModel.primary.isEnabled = true
                    stepModel.primary.text = "Model ready ✓"
                    Toast.makeText(this@MainActivity, "Speech model installed.", Toast.LENGTH_SHORT).show()
                    ParakeetEngine.get(this@MainActivity).preload()
                    refreshSetup()
                }
            }

            override fun onError(message: String) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    downloading = false
                    stepModel.progress?.visibility = View.GONE
                    stepModel.primary.isEnabled = true
                    stepModel.primary.text = "Download model"
                    stepModel.note?.text = "Download failed: $message"
                    setPill(stepModel.status, "FAILED", colors.danger)
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Download failed")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        })
    }

    private fun reinstallModel() {
        if (downloading) return
        AlertDialog.Builder(this)
            .setTitle("Re-download the model?")
            .setMessage("This removes the 126 MB model from this device and downloads it again.")
            .setPositiveButton("Re-download") { _, _ ->
                repository.reset()
                startDownload()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun onMicrophoneAction() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "The microphone is already allowed.", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 23 && shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            AlertDialog.Builder(this)
                .setTitle("Microphone access")
                .setMessage("Parakey needs the microphone only while you hold or tap the mic key. Audio is transcribed on this phone.")
                .setPositiveButton("Continue") { _, _ ->
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
                }
                .setNegativeButton("Not now", null)
                .show()
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            Toast.makeText(
                this,
                if (granted) "Microphone allowed" else "Microphone permission is needed for dictation",
                Toast.LENGTH_SHORT,
            ).show()
            refreshSetup()
        }
    }

    private fun openKeyboardSettings() {
        try {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            Toast.makeText(this, "Turn on Parakey, then come back.", Toast.LENGTH_LONG).show()
        } catch (_: Throwable) {
            Toast.makeText(this, "Could not open keyboard settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showKeyboardPicker() {
        val imm = getSystemService(InputMethodManager::class.java)
        imm?.showInputMethodPicker()
    }

    companion object {
        private const val REQUEST_MIC = 7101
    }
}
