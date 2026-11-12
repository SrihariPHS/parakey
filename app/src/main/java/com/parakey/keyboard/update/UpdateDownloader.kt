package com.parakey.keyboard.update

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Downloads a release APK into the app cache and verifies its SHA-256. */
class UpdateDownloader(private val context: Context) {

    interface Listener {
        fun onProgress(percent: Int, downloadedBytes: Long, totalBytes: Long)
        fun onReady(file: File)
        fun onError(message: String)
    }

    @Volatile
    private var cancelled = false

    @Volatile
    private var thread: Thread? = null

    fun download(info: UpdateInfo, listener: Listener) {
        if (thread?.isAlive == true) return
        cancelled = false
        thread = Thread({
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val target = File(dir, "parakey-${info.version}.apk")
                val part = File(dir, "parakey-${info.version}.apk.part")
                part.delete()
                val connection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Parakey/${android.os.Build.VERSION.RELEASE}")
                }
                try {
                    if (connection.responseCode !in 200..299) {
                        throw IllegalStateException("Download failed (HTTP ${connection.responseCode})")
                    }
                    val total = if (info.sizeBytes > 0) info.sizeBytes else connection.contentLengthLong
                    var done = 0L
                    connection.inputStream.use { input ->
                        FileOutputStream(part).use { output ->
                            val buffer = ByteArray(128 * 1024)
                            var last = 0L
                            while (true) {
                                if (cancelled) return@Thread
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                done += read
                                if (done - last >= 512 * 1024) {
                                    last = done
                                    val percent = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
                                    listener.onProgress(percent, done, total)
                                }
                            }
                            output.flush()
                        }
                    }
                    if (cancelled) return@Thread
                    val digest = UpdateChecker(context).sha256(part)
                    if (!digest.equals(info.sha256, ignoreCase = true)) {
                        part.delete()
                        throw IllegalStateException("The update failed its SHA-256 check")
                    }
                    if (target.exists()) target.delete()
                    if (!part.renameTo(target)) throw IllegalStateException("Could not store the update")
                    listener.onReady(target)
                } finally {
                    connection.disconnect()
                }
            } catch (t: Throwable) {
                if (!cancelled) listener.onError(t.message ?: t.javaClass.simpleName)
            }
        }, "parakey-update-downloader").apply { start() }
    }

    fun cancel() {
        cancelled = true
    }
}
