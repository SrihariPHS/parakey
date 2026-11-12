package com.parakey.keyboard.update

/**
 * Pure update decisions, kept free of Android APIs so they can be unit tested
 * and reused by the debug self-test.
 */
object UpdatePolicy {

    /** Compares dotted versions such as "v1.2.0" and "1.1.2". */
    fun isNewerVersion(latest: String, current: String): Boolean {
        val a = parts(latest)
        val b = parts(current)
        val size = maxOf(a.size, b.size)
        for (index in 0 until size) {
            val left = a.getOrElse(index) { 0 }
            val right = b.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    private fun parts(version: String): List<Int> {
        val cleaned = version.trim().removePrefix("v").removePrefix("V")
        val numeric = cleaned.takeWhile { it.isDigit() || it == '.' }
        return numeric.split('.').mapNotNull { it.toIntOrNull() }
    }

    /** Only GitHub release downloads from the project repository are accepted. */
    fun isTrustedApkUrl(url: String, repository: String = "SrihariPHS/parakey"): Boolean {
        if (!url.startsWith("https://github.com/")) return false
        val prefix = "https://github.com/$repository/releases/download/"
        return url.startsWith(prefix) && url.endsWith(".apk")
    }

    /** Accepts a bare digest or the format used by a .sha256 sidecar file. */
    fun parseSha256(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val match = Regex("(?i)\\b[a-f0-9]{64}\\b").find(value) ?: return null
        return match.value.lowercase()
    }

    fun summarizeReleaseNotes(body: String?, limit: Int = 280): String {
        val text = body.orEmpty()
            .replace("\r", "")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (text.length <= limit) text else text.take(limit).trimEnd() + "…"
    }
}
