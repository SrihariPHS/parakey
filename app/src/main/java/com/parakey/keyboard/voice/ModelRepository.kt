package com.parakey.keyboard.voice

import android.content.Context
import com.parakey.keyboard.BuildConfig
import java.io.File
import java.security.MessageDigest

class ModelRepository(context: Context) {

    private val appContext = context.applicationContext

    val externalRoot: File = File(
        appContext.getExternalFilesDir(null) ?: appContext.filesDir,
        "models/parakeet-110m",
    )

    val internalRoot: File = File(appContext.filesDir, "models/parakeet-110m")

    val modelFile: File
        get() = File(externalRoot, "model.int8.onnx")

    val tokensFile: File
        get() = File(externalRoot, "tokens.txt")

    val expectedModelBytes: Long = BuildConfig.MODEL_BYTES
    val expectedTokensBytes: Long = BuildConfig.TOKENS_BYTES

    fun prepare(): File {
        externalRoot.mkdirs()
        return externalRoot
    }

    fun resolveModel(): File? = listOf(
        File(externalRoot, "model.int8.onnx"),
        File(externalRoot, "model.onnx"),
        File(internalRoot, "model.int8.onnx"),
        File(internalRoot, "model.onnx"),
    ).firstOrNull { it.isFile && (it.length() == expectedModelBytes || expectedModelBytes <= 0) }

    fun resolveTokens(): File? = listOf(
        File(externalRoot, "tokens.txt"),
        File(internalRoot, "tokens.txt"),
    ).firstOrNull { it.isFile && (it.length() == expectedTokensBytes || expectedTokensBytes <= 0) }

    fun isInstalled(): Boolean = resolveModel() != null && resolveTokens() != null

    fun installedBytes(): Long = (resolveModel()?.length() ?: 0L) + (resolveTokens()?.length() ?: 0L)

    fun expectedBytes(): Long = expectedModelBytes + expectedTokensBytes

    fun reset() {
        externalRoot.deleteRecursively()
        internalRoot.deleteRecursively()
    }

    fun verifyChecksums(): Boolean {
        val model = resolveModel() ?: return false
        val tokens = resolveTokens() ?: return false
        return sha256(model) == BuildConfig.MODEL_SHA256 && sha256(tokens) == BuildConfig.TOKENS_SHA256
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

    fun statusLine(): String = when {
        isInstalled() -> "Ready · ${installedBytes() / (1024 * 1024)} MB on device"
        resolveModel() == null && resolveTokens() == null -> "Not installed"
        else -> "Incomplete download"
    }
}
