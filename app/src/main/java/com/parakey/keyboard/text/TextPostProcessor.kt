package com.parakey.keyboard.text

object TextPostProcessor {

    private val whitespace = Regex("\\s+")
    private val spaceBeforePunctuation = Regex("\\s+([,.;:!?])")

    fun clean(raw: String): String {
        var text = raw.replace(whitespace, " ").trim()
        if (text.isEmpty()) return ""
        text = spaceBeforePunctuation.replace(text, "$1")
        val first = text.firstOrNull() ?: return ""
        if (first.isLowerCase()) {
            text = first.uppercaseChar() + text.substring(1)
        }
        return text
    }

    fun prependSpaceIfNeeded(before: CharSequence?, dictated: String): String {
        if (dictated.isEmpty()) return dictated
        if (before.isNullOrEmpty()) return dictated
        val last = before.last()
        val first = dictated.first()
        val needsSpace = (last.isLetterOrDigit() || last == ',' || last == '.' || last == '!' || last == '?' || last == ';' || last == ':') &&
            (first.isLetterOrDigit() || first == '"' || first == '\'')
        return if (needsSpace) " $dictated" else dictated
    }

    fun wordCount(text: String): Int =
        text.split(whitespace).count { it.isNotBlank() }
}
