package com.parakey.keyboard.voice

import android.content.Context
import com.parakey.keyboard.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class ModelDownloader(private val context: Context) {

    interface Listener {
        fun onProgress(percent: Int, downloadedBytes: Long, totalBytes: Long)
        fun onComplete()
        fun onError(message: String)
    }

    private val repository = ModelRepository(context)

    @Volatile
    private var cancelled = false

    @Volatile
    private var thread: Thread? = null

    fun start(listener: Listener) {
        if (thread?.isAlive == true) return
        cancelled = false
        thread = Thread({
            try {
                val modelUrl = BuildConfig.MODEL_URL
                val tokensUrl = BuildConfig.TOKENS_URL
                if (modelUrl.isBlank() || tokensUrl.isBlank()) {
                    throw IllegalStateException(
                        "This build has no model download URL. Use scripts/push-model.sh or rebuild with -PmodelUrl=… -PtokensUrl=…",
                    )
                }
                repository.prepare()
                val total = BuildConfig.MODEL_BYTES + BuildConfig.TOKENS_BYTES
                var done = 0L

                if (repository.resolveTokens() == null) {
                    downloadFile(
                        tokensUrl,
                        repository.tokensFile,
                        BuildConfig.TOKENS_BYTES,
                        BuildConfig.TOKENS_SHA256,
                    ) { fileBytes ->
                        done = fileBytes
                        listener.onProgress(((done * 100) / total).toInt().coerceIn(0, 100), done, total)
                    }
                } else {
                    done += BuildConfig.TOKENS_BYTES
                    listener.onProgress(((done * 100) / total).toInt().coerceIn(0, 100), done, total)
                }

                if (cancelled) return@Thread
                if (repository.resolveModel() == null) {
                    downloadFile(
                        modelUrl,
                        repository.modelFile,
                        BuildConfig.MODEL_BYTES,
                        BuildConfig.MODEL_SHA256,
                    ) { fileBytes ->
                        done = BuildConfig.TOKENS_BYTES + fileBytes
                        listener.onProgress(((done * 100) / total).toInt().coerceIn(0, 100), done, total)
                    }
                } else {
                    done += BuildConfig.MODEL_BYTES
                    listener.onProgress(((done * 100) / total).toInt().coerceIn(0, 100), done, total)
                }

                if (cancelled) return@Thread
                if (!repository.isInstalled()) {
                    throw IllegalStateException("The model files are incomplete after download.")
                }
                listener.onComplete()
            } catch (t: Throwable) {
                if (!cancelled) {
                    listener.onError(t.message ?: t.javaClass.simpleName)
                }
            }
        }, "parakey-model-downloader").apply { start() }
    }

    fun cancel() {
        cancelled = true
    }

    private fun downloadFile(
        urlString: String,
        destination: File,
        expectedBytes: Long,
        expectedSha256: String,
        onChunk: (Long) -> Unit,
    ) {
        if (destination.isFile && destination.length() == expectedBytes) {
            if (sha256(destination).equals(expectedSha256, ignoreCase = true)) return
            destination.delete()
        }
        val parent = destination.parentFile ?: throw IllegalStateException("No destination directory")
        parent.mkdirs()
        val part = File(parent, destination.name + ".part")
        if (part.isFile && part.length() > expectedBytes) {
            part.delete()
        }
        var existing = if (part.isFile) part.length() else 0L
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Parakey/1.0 (Android)")
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                if (code == 416) {
                    part.delete()
                    throw IllegalStateException("The download range was rejected. Try again.")
                }
                throw IllegalStateException("Model download failed (HTTP $code).")
            }
            if (existing > 0 && code != HttpURLConnection.HTTP_PARTIAL) {
                part.delete()
                existing = 0L
            }
            val stream: InputStream = connection.inputStream
            FileOutputStream(part, existing > 0).use { output ->
                val buffer = ByteArray(128 * 1024)
                var lastReported = existing
                while (true) {
                    if (cancelled) return
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    existing += read
                    if (existing - lastReported >= 512 * 1024) {
                        lastReported = existing
                        onChunk(existing)
                    }
                }
                output.flush()
                onChunk(existing)
            }
            if (cancelled) return
            if (part.length() != expectedBytes) {
                throw IllegalStateException(
                    "Downloaded ${part.length()} bytes, expected $expectedBytes bytes.",
                )
            }
            val digest = sha256(part)
            if (!digest.equals(expectedSha256, ignoreCase = true)) {
                part.delete()
                throw IllegalStateException("The downloaded file failed its checksum. Try again.")
            }
            if (destination.exists()) destination.delete()
            if (!part.renameTo(destination)) {
                throw IllegalStateException("Could not move the downloaded model into place.")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
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
