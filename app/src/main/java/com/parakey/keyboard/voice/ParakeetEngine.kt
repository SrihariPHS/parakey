package com.parakey.keyboard.voice

import android.content.Context
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

data class Transcription(
    val text: String,
    val elapsedMs: Long,
    val audioMs: Long,
) {
    val realTimeFactor: Double
        get() = if (audioMs <= 0) 0.0 else elapsedMs.toDouble() / audioMs.toDouble()
}

class ParakeetEngine private constructor(context: Context) {

    companion object {
        private const val TAG = "ParakeetEngine"

        @Volatile
        private var instance: ParakeetEngine? = null

        fun get(context: Context): ParakeetEngine {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                instance ?: ParakeetEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    enum class State { NOT_LOADED, LOADING, READY, FAILED }

    private val modelRepository = ModelRepository(context)
    private val lock = Any()
    private val decodeLock = Any()
    private val callbacks = CopyOnWriteArrayList<(Boolean, String?) -> Unit>()

    @Volatile
    private var state: State = State.NOT_LOADED

    @Volatile
    private var lastError: String? = null

    @Volatile
    private var recognizer: OfflineRecognizer? = null

    fun state(): State = state

    fun lastError(): String? = lastError

    fun isReady(): Boolean = state == State.READY && recognizer != null

    fun preload(callback: ((Boolean, String?) -> Unit)? = null) {
        if (callback != null) callbacks.add(callback)
        synchronized(lock) {
            when (state) {
                State.READY -> {
                    lastError = null
                    val readyCallbacks = ArrayList(callbacks)
                    callbacks.clear()
                    readyCallbacks.forEach { it(true, null) }
                }
                State.LOADING -> Unit
                State.FAILED, State.NOT_LOADED -> {
                    state = State.LOADING
                    Thread({ load() }, "parakeet-model-loader").start()
                }
            }
        }
    }

    private fun load() {
        try {
            val modelFile = modelRepository.resolveModel()
                ?: throw IllegalStateException("Speech model is not installed. Open Parakey to download it.")
            val tokensFile = modelRepository.resolveTokens()
                ?: throw IllegalStateException("The model tokenizer is missing. Reinstall the speech model.")
            if (!modelRepository.verifyChecksums()) {
                throw IllegalStateException("The speech model failed its integrity check. Reinstall it.")
            }
            runCatching { System.loadLibrary("sherpa-onnx-jni") }
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    nemo = OfflineNemoEncDecCtcModelConfig(model = modelFile.absolutePath),
                    tokens = tokensFile.absolutePath,
                    numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
                    provider = "cpu",
                    modelType = "nemo_ctc",
                    debug = false,
                ),
                decodingMethod = "greedy_search",
            )
            val engine = OfflineRecognizer(config = config)
            // Warm up ONNX Runtime and the feature extractor before the first real dictation.
            runCatching {
                val stream = engine.createStream()
                try {
                    stream.acceptWaveform(FloatArray(8_000), 16_000)
                    engine.decode(stream)
                    engine.getResult(stream)
                } finally {
                    stream.release()
                }
            }
            synchronized(lock) {
                recognizer = engine
                state = State.READY
                lastError = null
                val readyCallbacks = ArrayList(callbacks)
                callbacks.clear()
                readyCallbacks.forEach { it(true, null) }
            }
        } catch (t: Throwable) {
            val message = t.message ?: t.javaClass.simpleName
            synchronized(lock) {
                recognizer?.release()
                recognizer = null
                state = State.FAILED
                lastError = message
                val failedCallbacks = ArrayList(callbacks)
                callbacks.clear()
                failedCallbacks.forEach { it(false, message) }
            }
        }
    }

    fun awaitReady(timeoutMs: Long): Boolean {
        if (isReady()) return true
        preload()
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (isReady()) return true
            if (state == State.FAILED) return false
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
                return false
            }
        }
        return isReady()
    }

    fun transcribe(samples: FloatArray, sampleRate: Int = 16_000): Transcription {
        val engine = recognizer
            ?: throw IllegalStateException(lastError ?: "Speech model is not ready.")
        if (samples.isEmpty()) return Transcription("", 0, 0)
        val audio = if (sampleRate == 16_000) samples else resample(samples, sampleRate, 16_000)
        val started = SystemClock.uptimeMillis()
        synchronized(decodeLock) {
            val stream = engine.createStream()
            try {
                stream.acceptWaveform(audio, 16_000)
                engine.decode(stream)
                val result = engine.getResult(stream)
                val elapsed = SystemClock.uptimeMillis() - started
                return Transcription(
                    text = result.text.trim(),
                    elapsedMs = elapsed,
                    audioMs = audio.size * 1000L / 16_000,
                )
            } finally {
                stream.release()
            }
        }
    }

    fun transcribeWav(file: File): Transcription {
        val wav = WavReader.read(file)
        return transcribe(wav.samples, wav.sampleRate)
    }

    fun release() {
        synchronized(decodeLock) {
            synchronized(lock) {
                try {
                    recognizer?.release()
                } catch (_: Throwable) {
                }
                recognizer = null
                state = State.NOT_LOADED
            }
        }
    }

    private fun resample(input: FloatArray, from: Int, to: Int): FloatArray {
        if (from <= 0 || input.isEmpty()) return FloatArray(0)
        val output = FloatArray((input.size.toLong() * to / from).toInt().coerceAtLeast(1))
        val ratio = from.toDouble() / to.toDouble()
        for (i in output.indices) {
            val source = i * ratio
            val i0 = source.toInt().coerceIn(0, input.size - 1)
            val i1 = (i0 + 1).coerceAtMost(input.size - 1)
            val frac = (source - i0).toFloat()
            output[i] = input[i0] * (1f - frac) + input[i1] * frac
        }
        return output
    }
}
