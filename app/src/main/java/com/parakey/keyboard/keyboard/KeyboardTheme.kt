package com.parakey.keyboard.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

data class KeyboardPalette(
    val background: Int,
    val key: Int,
    val keyPressed: Int,
    val special: Int,
    val specialPressed: Int,
    val text: Int,
    val hint: Int,
    val accent: Int,
    val accentPressed: Int,
    val onAccent: Int,
    val suggestionBg: Int,
    val suggestionText: Int,
    val border: Int,
    val shadow: Int,
    val danger: Int,
    val warning: Int,
    val overlay: Int,
)

data class KeyboardDimens(
    val topBar: Float = 52f,
    val row: Float = 52f,
    val gap: Float = 6f,
    val sidePad: Float = 4f,
    val keyRadius: Float = 9f,
    val letterSize: Float = 22f,
    val specialSize: Float = 12.5f,
    val emojiSize: Float = 24f,
    val micRadius: Float = 17f,
    val gearRadius: Float = 17f,
    val voiceMicRadius: Float = 46f,
    val topBarMicCenterFromRight: Float = 28f,
    val topBarGearCenterFromRight: Float = 70f,
    val suggestionHeight: Float = 36f,
)

object KeyboardTheme {

    fun isNight(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    fun palette(context: Context, mode: String? = null): KeyboardPalette {
        val forceDark = when (mode) {
            "dark" -> true
            "light" -> false
            else -> null
        }
        val dark = forceDark ?: isNight(context)
        return if (dark) darkPalette() else lightPalette()
    }

    private fun lightPalette() = KeyboardPalette(
        background = Color.rgb(238, 241, 246),
        key = Color.WHITE,
        keyPressed = Color.rgb(226, 232, 240),
        special = Color.rgb(221, 227, 234),
        specialPressed = Color.rgb(203, 213, 225),
        text = Color.rgb(15, 23, 42),
        hint = Color.rgb(100, 116, 139),
        accent = Color.rgb(18, 184, 134),
        accentPressed = Color.rgb(11, 143, 106),
        onAccent = Color.WHITE,
        suggestionBg = Color.WHITE,
        suggestionText = Color.rgb(71, 85, 105),
        border = Color.rgb(226, 232, 240),
        shadow = Color.argb(18, 15, 23, 42),
        danger = Color.rgb(225, 29, 72),
        warning = Color.rgb(217, 119, 6),
        overlay = Color.rgb(248, 250, 252),
    )

    private fun darkPalette() = KeyboardPalette(
        background = Color.rgb(15, 23, 42),
        key = Color.rgb(30, 41, 59),
        keyPressed = Color.rgb(51, 65, 85),
        special = Color.rgb(38, 52, 73),
        specialPressed = Color.rgb(51, 65, 85),
        text = Color.rgb(241, 245, 249),
        hint = Color.rgb(148, 163, 184),
        accent = Color.rgb(45, 212, 191),
        accentPressed = Color.rgb(20, 184, 166),
        onAccent = Color.rgb(4, 47, 46),
        suggestionBg = Color.rgb(30, 41, 59),
        suggestionText = Color.rgb(203, 213, 225),
        border = Color.rgb(38, 52, 73),
        shadow = Color.argb(64, 0, 0, 0),
        danger = Color.rgb(251, 113, 133),
        warning = Color.rgb(251, 191, 36),
        overlay = Color.rgb(15, 23, 42),
    )
}
