package com.parakey.keyboard

import android.os.Bundle
import android.util.Log
import com.parakey.keyboard.voice.AudioRecorder
import com.parakey.keyboard.voice.ModelRepository
import com.parakey.keyboard.voice.ParakeetEngine
import com.parakey.keyboard.voice.WavReader
import org.json.JSONObject
import java.io.File
import kotlin.math.sqrt

class SelfTestActivity : android.app.Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent?.getStringExtra("mode") ?: "wav"
        val out = intent?.getStringExtra("out")
        val wavPath = intent?.getStringExtra("wav")
        val durationMs = intent?.getIntExtra("durationMs", 4_000) ?: 4_000
        val loopback = intent?.getBooleanExtra("loopback", false) ?: false
        Thread({
            val result = try {
                when (mode) {
                    "wav" -> runWav(wavPath)
                    "record" -> runRecord(durationMs, loopback, wavPath)
                    "mic_asr" -> runMicAsr(durationMs, loopback, wavPath)
                    "empty" -> runEmpty()
                    "short" -> runShort()
                    else -> JSONObject().put("ok", false).put("error", "Unknown mode: $mode")
                }
            } catch (t: Throwable) {
                JSONObject().put("ok", false).put("error", t.message ?: t.javaClass.simpleName)
            }
            result.put("mode", mode)
            writeOut(out, result)
            Log.i("ParakeySelfTest", result.toString())
            runOnUiThread { finish() }
        }, "parakey-selftest").start()
    }

    private fun runWav(wavPath: String?): JSONObject {
        val file = resolveFile(wavPath) ?: return JSONObject().put("ok", false).put("error", "WAV path is missing")
        if (!file.isFile) return JSONObject().put("ok", false).put("error", "WAV not found: ${file.absolutePath}")
        val engine = ParakeetEngine.get(this)
        if (!engine.awaitReady(120_000)) {
            return JSONObject().put("ok", false).put("error", engine.lastError() ?: "Model not ready")
        }
        val wav = WavReader.read(file)
        val started = System.currentTimeMillis()
        val transcription = engine.transcribe(wav.samples, wav.sampleRate)
        val elapsed = System.currentTimeMillis() - started
        return JSONObject()
            .put("ok", true)
            .put("text", transcription.text)
            .put("elapsedMs", elapsed)
            .put("audioMs", transcription.audioMs)
            .put("sampleRate", wav.sampleRate)
            .put("sampleCount", wav.samples.size)
            .put("rtf", if (transcription.audioMs > 0) elapsed.toDouble() / transcription.audioMs else 0.0)
    }

    private fun runEmpty(): JSONObject {
        val engine = ParakeetEngine.get(this)
        if (!engine.awaitReady(120_000)) {
            return JSONObject().put("ok", false).put("error", engine.lastError() ?: "Model not ready")
        }
        val started = System.currentTimeMillis()
        val transcription = engine.transcribe(FloatArray(0))
        return JSONObject()
            .put("ok", true)
            .put("text", transcription.text)
            .put("elapsedMs", System.currentTimeMillis() - started)
            .put("audioMs", 0)
    }

    private fun runShort(): JSONObject {
        val engine = ParakeetEngine.get(this)
        if (!engine.awaitReady(120_000)) {
            return JSONObject().put("ok", false).put("error", engine.lastError() ?: "Model not ready")
        }
        val started = System.currentTimeMillis()
        val samples = FloatArray(1_600) { i -> if (i % 97 == 0) 0.01f else 0f }
        val transcription = engine.transcribe(samples)
        return JSONObject()
            .put("ok", true)
            .put("text", transcription.text)
            .put("elapsedMs", System.currentTimeMillis() - started)
            .put("audioMs", 100)
    }

    private fun runRecord(durationMs: Int, loopback: Boolean, wavPath: String?): JSONObject {
        val stats = record(durationMs, loopback, wavPath)
        return JSONObject()
            .put("ok", true)
            .put("sampleCount", stats.samples.size)
            .put("durationMs", stats.durationMs)
            .put("peak", stats.peakLevel)
            .put("average", stats.averageLevel)
            .put("loopback", loopback)
    }

    private fun runMicAsr(durationMs: Int, loopback: Boolean, wavPath: String?): JSONObject {
        val stats = record(durationMs, loopback, wavPath)
        if (stats.samples.isEmpty()) {
            return JSONObject().put("ok", false).put("error", "No microphone samples recorded")
        }
        val engine = ParakeetEngine.get(this)
        if (!engine.awaitReady(120_000)) {
            return JSONObject().put("ok", false).put("error", engine.lastError() ?: "Model not ready")
        }
        val started = System.currentTimeMillis()
        val transcription = engine.transcribe(stats.samples, 16_000)
        val elapsed = System.currentTimeMillis() - started
        return JSONObject()
            .put("ok", true)
            .put("text", transcription.text)
            .put("elapsedMs", elapsed)
            .put("audioMs", stats.durationMs)
            .put("sampleCount", stats.samples.size)
            .put("peak", stats.peakLevel)
            .put("average", stats.averageLevel)
            .put("rtf", if (stats.durationMs > 0) elapsed.toDouble() / stats.durationMs else 0.0)
            .put("loopback", loopback)
    }

    private fun record(durationMs: Int, loopback: Boolean, wavPath: String?): com.parakey.keyboard.voice.RecordingResult {
        val latch = java.util.concurrent.CountDownLatch(1)
        val recorder = AudioRecorder(
            onAmplitude = {},
            onError = { latch.countDown() },
            onAutoStop = { latch.countDown() },
            maxDurationMs = durationMs,
        )
        if (!recorder.start()) {
            latch.countDown()
        }
        if (loopback) {
            val file = resolveFile(wavPath)
            if (file != null && file.isFile) {
                Thread({
                    Thread.sleep(350)
                    WavPlayer.play(this, file)
                }, "parakey-loopback").start()
            }
        }
        latch.await(durationMs + 3_000L, java.util.concurrent.TimeUnit.MILLISECONDS)
        return recorder.stop() ?: com.parakey.keyboard.voice.RecordingResult(
            FloatArray(0),
            0,
            0f,
            0f,
        )
    }

    private fun resolveFile(path: String?): File? {
        if (path.isNullOrBlank()) return null
        val base = getExternalFilesDir(null) ?: filesDir
        return if (path.startsWith("/")) File(path) else File(base, path)
    }

    private fun writeOut(out: String?, result: JSONObject) {
        if (out.isNullOrBlank()) return
        try {
            val base = getExternalFilesDir(null) ?: filesDir
            val file = if (out.startsWith("/")) File(out) else File(base, out)
            file.parentFile?.mkdirs()
            file.writeText(result.toString())
        } catch (t: Throwable) {
            Log.e("ParakeySelfTest", "Could not write $out", t)
        }
    }
}
