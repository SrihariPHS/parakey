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
    val numberSize: Float = 15f,
    val micRadius: Float = 17f,
    val gearRadius: Float = 17f,
    val voiceMicRadius: Float = 46f,
    val topBarMicCenterFromRight: Float = 28f,
    val topBarGearCenterFromRight: Float = 70f,
    val suggestionHeight: Float = 36f,
)

data class AccentPalette(
    val color: Int,
    val pressed: Int,
    val onColor: Int,
)

object KeyboardTheme {

    val accents = listOf("mint", "blue", "violet", "sunset", "rose")

    fun isNight(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    fun accentPalette(name: String, dark: Boolean): AccentPalette = when (name.lowercase()) {
        "blue" -> if (dark) {
            AccentPalette(Color.rgb(96, 165, 250), Color.rgb(59, 130, 246), Color.rgb(8, 47, 73))
        } else {
            AccentPalette(Color.rgb(37, 99, 235), Color.rgb(29, 78, 216), Color.WHITE)
        }
        "violet" -> if (dark) {
            AccentPalette(Color.rgb(167, 139, 250), Color.rgb(139, 92, 246), Color.rgb(46, 16, 101))
        } else {
            AccentPalette(Color.rgb(124, 58, 237), Color.rgb(109, 40, 217), Color.WHITE)
        }
        "sunset" -> if (dark) {
            AccentPalette(Color.rgb(251, 146, 60), Color.rgb(249, 115, 22), Color.rgb(67, 20, 7))
        } else {
            AccentPalette(Color.rgb(194, 65, 12), Color.rgb(154, 52, 18), Color.WHITE)
        }
        "rose" -> if (dark) {
            AccentPalette(Color.rgb(251, 113, 133), Color.rgb(244, 63, 94), Color.rgb(76, 5, 25))
        } else {
            AccentPalette(Color.rgb(190, 18, 60), Color.rgb(159, 18, 57), Color.WHITE)
        }
        else -> if (dark) {
            AccentPalette(Color.rgb(45, 212, 191), Color.rgb(20, 184, 166), Color.rgb(4, 47, 46))
        } else {
            AccentPalette(Color.rgb(12, 124, 91), Color.rgb(9, 100, 74), Color.WHITE)
        }
    }

    fun palette(
        context: Context,
        mode: String? = null,
        accentName: String = "mint",
    ): KeyboardPalette {
        val amoled = mode == "amoled"
        val forceDark = when (mode) {
            "dark", "amoled" -> true
            "light" -> false
            else -> null
        }
        val dark = forceDark ?: isNight(context)
        val accent = accentPalette(accentName, dark)
        return when {
            amoled -> darkPalette(accent).copy(
                background = Color.BLACK,
                key = Color.rgb(10, 10, 10),
                keyPressed = Color.rgb(28, 28, 28),
                special = Color.rgb(20, 20, 20),
                specialPressed = Color.rgb(36, 36, 36),
                suggestionBg = Color.rgb(18, 18, 18),
                border = Color.rgb(40, 40, 40),
                overlay = Color.BLACK,
            )
            dark -> darkPalette(accent)
            else -> lightPalette(accent)
        }
    }

    private fun lightPalette(accent: AccentPalette) = KeyboardPalette(
        background = Color.rgb(238, 241, 246),
        key = Color.WHITE,
        keyPressed = Color.rgb(226, 232, 240),
        special = Color.rgb(221, 227, 234),
        specialPressed = Color.rgb(203, 213, 225),
        text = Color.rgb(15, 23, 42),
        hint = Color.rgb(91, 107, 128),
        accent = accent.color,
        accentPressed = accent.pressed,
        onAccent = accent.onColor,
        suggestionBg = Color.WHITE,
        suggestionText = Color.rgb(71, 85, 105),
        border = Color.rgb(226, 232, 240),
        shadow = Color.argb(18, 15, 23, 42),
        danger = Color.rgb(225, 29, 72),
        warning = Color.rgb(217, 119, 6),
        overlay = Color.rgb(248, 250, 252),
    )

    private fun darkPalette(accent: AccentPalette) = KeyboardPalette(
        background = Color.rgb(15, 23, 42),
        key = Color.rgb(30, 41, 59),
        keyPressed = Color.rgb(51, 65, 85),
        special = Color.rgb(38, 52, 73),
        specialPressed = Color.rgb(51, 65, 85),
        text = Color.rgb(241, 245, 249),
        hint = Color.rgb(148, 163, 184),
        accent = accent.color,
        accentPressed = accent.pressed,
        onAccent = accent.onColor,
        suggestionBg = Color.rgb(30, 41, 59),
        suggestionText = Color.rgb(203, 213, 225),
        border = Color.rgb(38, 52, 73),
        shadow = Color.argb(64, 0, 0, 0),
        danger = Color.rgb(251, 113, 133),
        warning = Color.rgb(251, 191, 36),
        overlay = Color.rgb(15, 23, 42),
    )
}
