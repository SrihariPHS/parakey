package com.parakey.keyboard.update

import android.content.Context
import com.parakey.keyboard.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val releaseUrl: String,
)

/** Talks to the GitHub releases API. No account or token is used. */
class UpdateChecker(private val context: Context) {

    fun check(includeCurrentRelease: Boolean = false): Result<UpdateInfo?> = runCatching {
        val repo = BuildConfig.UPDATE_REPO
        val connection = (URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Parakey/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (connection.responseCode != 200) {
                throw IllegalStateException("GitHub returned HTTP ${connection.responseCode}")
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (json.optBoolean("draft") || json.optBoolean("prerelease")) return@runCatching null
            val tag = json.optString("tag_name")
            if (!includeCurrentRelease && !UpdatePolicy.isNewerVersion(tag, BuildConfig.VERSION_NAME)) {
                return@runCatching null
            }
            val assets = json.optJSONArray("assets") ?: throw IllegalStateException("Release has no assets")
            var apkUrl = ""
            var digest = ""
            var size = 0L
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                when (asset.optString("name")) {
                    "app-release.apk" -> {
                        apkUrl = asset.optString("browser_download_url")
                        digest = asset.optString("digest")
                        size = asset.optLong("size")
                    }
                }
            }
            if (!UpdatePolicy.isTrustedApkUrl(apkUrl, repo)) {
                throw IllegalStateException("Release APK URL is not trusted")
            }
            var sha = UpdatePolicy.parseSha256(digest)
            if (sha == null) sha = fetchSidecarSha(apkUrl)
            if (sha == null) throw IllegalStateException("Release has no SHA-256 digest")
            UpdateInfo(
                version = tag.removePrefix("v"),
                notes = UpdatePolicy.summarizeReleaseNotes(json.optString("body")),
                apkUrl = apkUrl,
                sha256 = sha,
                sizeBytes = size,
                releaseUrl = json.optString("html_url"),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchSidecarSha(apkUrl: String): String? {
        val sidecar = apkUrl + ".sha256"
        val connection = (URL(sidecar).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Parakey/${BuildConfig.VERSION_NAME}")
        }
        return try {
            if (connection.responseCode != 200) return null
            UpdatePolicy.parseSha256(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}
