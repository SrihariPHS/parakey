package com.parakey.keyboard

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.parakey.keyboard.ui.colorWithAlpha
import com.parakey.keyboard.ui.dp
import com.parakey.keyboard.ui.roundedDrawable
import com.parakey.keyboard.ui.setRoundedBackground

class TryActivity : android.app.Activity() {

    companion object {
        @Volatile
        var instance: TryActivity? = null
            private set
    }

    private data class Colors(
        val background: Int,
        val card: Int,
        val text: Int,
        val muted: Int,
        val border: Int,
        val accent: Int,
        val onAccent: Int,
    )

    private lateinit var colors: Colors
    private lateinit var settings: SettingsStore
    private var night = false
    private lateinit var message: EditText
    private lateinit var name: EditText
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var status: TextView
    private lateinit var scroll: ScrollView
    private lateinit var clearButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = SettingsStore(this)
        night = when (settings.theme) {
            "dark" -> true
            "light" -> false
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
        setTheme(if (night) R.style.Theme_Parakey_Dark else R.style.Theme_Parakey)
        super.onCreate(savedInstanceState)
        colors = if (night) {
            Colors(
                background = Color.rgb(15, 23, 42),
                card = Color.rgb(30, 41, 59),
                text = Color.rgb(241, 245, 249),
                muted = Color.rgb(148, 163, 184),
                border = Color.rgb(51, 65, 85),
                accent = Color.rgb(45, 212, 191),
                onAccent = Color.rgb(4, 47, 46),
            )
        } else {
            Colors(
                background = Color.rgb(246, 248, 251),
                card = Color.WHITE,
                text = Color.rgb(15, 23, 42),
                muted = Color.rgb(100, 116, 139),
                border = Color.rgb(226, 232, 240),
                accent = Color.rgb(18, 184, 134),
                onAccent = Color.WHITE,
            )
        }
        buildUi()
        configureWindow()
        handleFocusIntent()
    }

    private fun configureWindow() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.decorView.post {
                val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                window.insetsController?.setSystemBarsAppearance(if (night) 0 else mask, mask)
            }
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE,
        )
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.getStringExtra("action") == "clear") {
            debugClickClear()
            return
        }
        intent?.getStringExtra("focus")?.let { debugFocus(it) }
    }

    fun debugClickClear(): Boolean {
        if (!::clearButton.isInitialized) return false
        clearButton.performClick()
        return true
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun debugFocus(which: String): Boolean {
        val field = when (which) {
            "message" -> message
            "name" -> name
            "email" -> email
            "password" -> password
            else -> return false
        }
        field.requestFocus()
        field.setSelection(field.text.length)
        field.post {
            field.requestRectangleOnScreen(Rect(0, 0, field.width, field.height), true)
        }
        getSystemService(InputMethodManager::class.java)?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        return true
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colors.background)
        }
        setContentView(root)
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                val ime = insets.getInsets(WindowInsets.Type.ime()).bottom
                view.setPadding(0, bars.top, 0, maxOf(bars.bottom, ime))
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            }
            insets
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(16), dp(10))
            setBackgroundColor(colors.card)
        }
        val back = text("←", 24f, colors.text, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setOnClickListener { finish() }
        }
        topBar.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, 0, 0)
        }
        titles.addView(text("Test pad", 18f, colors.text, bold = true))
        titles.addView(text("Parakey keyboard", 11f, colors.muted))
        topBar.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        val badge = text("LIVE", 9f, colors.accent, bold = true).apply {
            letterSpacing = 0.08f
            setPadding(dp(9), dp(4), dp(9), dp(4))
            setRoundedBackground(colorWithAlpha(colors.accent, 24), 50f)
        }
        topBar.addView(badge)
        root.addView(topBar, LinearLayout.LayoutParams(-1, -2))

        scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(20))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(text("Send a message", 22f, colors.text, bold = true))
        content.addView(text(
            "Type with the keys, or tap the mint mic and speak. Everything stays on this phone.",
            12.5f,
            colors.muted,
        ).apply { setPadding(0, dp(5), 0, dp(12)) })

        val messageCard = card()
        message = field(
            id = R.id.field_message,
            hint = "Write something…",
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
            minLines = 7,
            singleLine = false,
        )
        message.contentDescription = "message field"
        messageCard.addView(fieldLabel("Message"))
        messageCard.addView(message)
        content.addView(messageCard)

        content.addView(section("Privacy check"))
        val passwordCard = card()
        passwordCard.addView(fieldLabel("Password field"))
        password = field(
            id = R.id.field_password,
            hint = "The mic is disabled here",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            minLines = 1,
            singleLine = true,
        )
        password.contentDescription = "password field"
        password.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        passwordCard.addView(password)
        passwordCard.addView(text(
            "Password fields never offer voice typing. This is a privacy guard, not a setting.",
            10.5f,
            colors.muted,
        ).apply { setPadding(0, dp(7), 0, 0) })
        content.addView(passwordCard)

        content.addView(section("Contact form"))
        val formCard = card()
        formCard.addView(fieldLabel("Name"))
        name = field(
            id = R.id.field_name,
            hint = "Jordan Lee",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS,
            minLines = 1,
            singleLine = true,
        )
        name.contentDescription = "name field"
        name.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
        formCard.addView(name)
        formCard.addView(fieldLabel("Email", top = 10))
        email = field(
            id = R.id.field_email,
            hint = "jordan@example.com",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            minLines = 1,
            singleLine = true,
        )
        email.contentDescription = "email field"
        email.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        formCard.addView(email)
        content.addView(formCard)

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        status = text("Ready", 11f, colors.muted).apply {
            id = R.id.status_text
        }
        bottom.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        clearButton = text("Clear all", 12f, colors.accent, bold = true).apply {
            id = R.id.btn_clear
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(9), dp(14), dp(9))
            setRoundedBackground(colorWithAlpha(colors.accent, 20), 10f)
            setOnClickListener {
                message.setText("")
                name.setText("")
                email.setText("")
                password.setText("")
                focusField(message)
            }
        }
        bottom.addView(clearButton)
        content.addView(bottom)
    }

    private fun handleFocusIntent() {
        val which = intent?.getStringExtra("focus") ?: "message"
        val field = when (which) {
            "password" -> password
            "name" -> name
            "email" -> email
            else -> message
        }
        field.postDelayed({ focusField(field) }, 350)
    }

    private fun focusField(field: EditText) {
        field.requestFocus()
        field.setSelection(field.text.length)
        val imm = getSystemService(InputMethodManager::class.java)
        imm?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        status.text = "Keyboard: Parakey · field focused"
    }

    private fun fieldLabel(text: String, top: Int = 0): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(colors.muted)
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(top.toFloat()), 0, dp(5))
        }

    private fun section(text: String): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(colors.muted)
            textSize = 10.5f
            letterSpacing = 0.12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(4), dp(18), dp(4), dp(8))
        }

    private fun card(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = roundedDrawable(this@TryActivity, colors.card, 16f, colors.border, 1f)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        }

    private fun field(
        id: Int,
        hint: String,
        inputType: Int,
        minLines: Int,
        singleLine: Boolean,
    ): EditText =
        EditText(this).apply {
            this.id = id
            this.hint = hint
            // Configure single-line mode before the input type so the password
            // variation is not lost by TextView's single-line flag handling.
            this.isSingleLine = singleLine
            this.inputType = inputType
            this.minLines = minLines
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            setHintTextColor(colors.muted)
            setTextColor(colors.text)
            textSize = 16f
            gravity = if (singleLine) Gravity.CENTER_VERTICAL else Gravity.TOP or Gravity.START
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setRoundedBackground(colors.background, 11f, strokeColor = colors.border, strokeWidthDp = 1f)
            if (!singleLine) {
                layoutParams = LinearLayout.LayoutParams(-1, -2)
                setLines(minLines)
            }
        }

    private fun text(text: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(color)
            textSize = sizeSp
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
        }
}
