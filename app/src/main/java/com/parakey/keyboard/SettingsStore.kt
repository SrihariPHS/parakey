package com.parakey.keyboard

import android.content.Context

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("parakey_settings", Context.MODE_PRIVATE)

    var theme: String
        get() = prefs.getString("theme", "system") ?: "system"
        set(value) {
            prefs.edit().putString("theme", value).apply()
        }

    var accent: String
        get() = prefs.getString("accent", "mint") ?: "mint"
        set(value) {
            prefs.edit().putString("accent", value).apply()
        }

    var numberRowEnabled: Boolean
        get() = prefs.getBoolean("number_row", false)
        set(value) {
            prefs.edit().putBoolean("number_row", value).apply()
        }

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(value) {
            prefs.edit().putBoolean("haptics", value).apply()
        }

    var soundEnabled: Boolean
        get() = prefs.getBoolean("sound", true)
        set(value) {
            prefs.edit().putBoolean("sound", value).apply()
        }

    var showLivePreview: Boolean
        get() = prefs.getBoolean("live_preview", false)
        set(value) {
            prefs.edit().putBoolean("live_preview", value).apply()
        }

    var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(value) {
            prefs.edit().putBoolean("onboarding_done", value).apply()
        }

    var autocorrectEnabled: Boolean
        get() = prefs.getBoolean("autocorrect", true)
        set(value) {
            prefs.edit().putBoolean("autocorrect", value).apply()
        }

    var suggestionsEnabled: Boolean
        get() = prefs.getBoolean("suggestions", true)
        set(value) {
            prefs.edit().putBoolean("suggestions", value).apply()
        }

    var autoCapitalizeEnabled: Boolean
        get() = prefs.getBoolean("auto_capitalize", true)
        set(value) {
            prefs.edit().putBoolean("auto_capitalize", value).apply()
        }

    private val listSeparator = "\u001F"

    var personalWords: Set<String>
        get() = prefs.getString("personal_words", "")
            .orEmpty()
            .split(listSeparator)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        set(value) {
            prefs.edit().putString("personal_words", value.take(250).joinToString(listSeparator)).apply()
        }

    fun addPersonalWord(word: String) {
        val clean = word.trim()
        if (clean.length < 2) return
        personalWords = personalWords + clean
    }

    fun removePersonalWord(word: String) {
        personalWords = personalWords - word
    }

    private val clipboardSeparator = "\u001F"

    var clipboardHistory: List<String>
        get() = prefs.getString("clipboard_history", "")
            .orEmpty()
            .split(clipboardSeparator)
            .filter { it.isNotBlank() }
        set(value) {
            prefs.edit().putString("clipboard_history", value.take(5).joinToString(clipboardSeparator)).apply()
        }

    fun addClipboardHistory(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        clipboardHistory = listOf(clean) + clipboardHistory.filter { it != clean }
    }

    fun clearClipboardHistory() {
        prefs.edit().remove("clipboard_history").apply()
    }

    var emojiRecents: List<String>
        get() = prefs.getString("emoji_recents", "")
            .orEmpty()
            .split(listSeparator)
            .filter { it.isNotBlank() }
        set(value) {
            prefs.edit().putString("emoji_recents", value.take(8).joinToString(listSeparator)).apply()
        }

    fun addEmojiRecent(emoji: String) {
        val clean = emoji.trim()
        if (clean.isEmpty()) return
        emojiRecents = listOf(clean) + emojiRecents.filter { it != clean }
    }
}
