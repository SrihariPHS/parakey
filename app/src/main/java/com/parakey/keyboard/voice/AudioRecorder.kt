package com.parakey.keyboard.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.sqrt

data class RecordingResult(
    val samples: FloatArray,
    val durationMs: Long,
    val peakLevel: Float,
    val averageLevel: Float,
)

class AudioRecorder(
    private val onAmplitude: (Float) -> Unit,
    private val onError: (String) -> Unit,
    private val onAutoStop: () -> Unit,
    private val maxDurationMs: Int = 60_000,
) {
    companion object {
        /**
         * Tests can force the raw MIC source to defeat acoustic echo
         * cancellation during speaker-loopback checks. Production dictation
         * keeps VOICE_RECOGNITION.
         */
        @Volatile
        var forceSource: Int? = null
    }

    private val sampleRate = 16_000
    private val lock = Any()
    private val chunks = ArrayList<ShortArray>()
    private var totalSamples = 0
    private var record: AudioRecord? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    private var started = false

    @Volatile
    private var lastPeak = 0f

    @Volatile
    private var sumLevel = 0.0

    @Volatile
    private var levelCount = 0

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (started) return true
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            onError("This device could not provide a microphone buffer.")
            return false
        }
        val bufferSize = maxOf(minBuffer, sampleRate * 2 / 4)
        val audioRecord = try {
            AudioRecord.Builder()
                .setAudioSource(forceSource ?: MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferSize)
                .build()
        } catch (t: Throwable) {
            onError("Could not create the microphone: ${t.message ?: t.javaClass.simpleName}")
            return false
        }
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            onError("The microphone is unavailable right now.")
            return false
        }
        record = audioRecord
        running = true
        started = true
        lastPeak = 0f
        sumLevel = 0.0
        levelCount = 0
        synchronized(lock) {
            chunks.clear()
            totalSamples = 0
        }
        try {
            audioRecord.startRecording()
        } catch (t: Throwable) {
            running = false
            started = false
            audioRecord.release()
            record = null
            onError("Could not start recording: ${t.message ?: t.javaClass.simpleName}")
            return false
        }
        thread = Thread({ recordLoop(audioRecord, bufferSize) }, "parakey-audio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        return true
    }

    private fun recordLoop(audioRecord: AudioRecord, bufferSize: Int) {
        val buffer = ShortArray(maxOf(800, bufferSize / 2))
        while (running) {
            val read = try {
                audioRecord.read(buffer, 0, buffer.size)
            } catch (t: Throwable) {
                if (running) onError("Microphone read failed: ${t.message ?: t.javaClass.simpleName}")
                break
            }
            if (read <= 0) {
                if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) {
                    onError("Microphone read failed ($read).")
                    break
                }
                continue
            }
            val copy = buffer.copyOf(read)
            synchronized(lock) {
                chunks.add(copy)
                totalSamples += read
            }
            var sum = 0.0
            var peak = 0f
            for (sample in copy) {
                val value = sample / 32768f
                val abs = kotlin.math.abs(value)
                if (abs > peak) peak = abs
                sum += value.toDouble() * value
            }
            val rms = sqrt(sum / read).toFloat()
            lastPeak = maxOf(lastPeak, peak)
            sumLevel += rms
            levelCount++
            val level = (rms * 9f).coerceIn(0.02f, 1f)
            onAmplitude(level)
            synchronized(lock) {
                if (totalSamples * 1000L / sampleRate >= maxDurationMs) {
                    running = false
                }
            }
            if (!running) {
                onAutoStop()
            }
        }
    }

    fun stop(): RecordingResult? {
        if (!started) return null
        running = false
        val audioRecord = record
        try {
            audioRecord?.stop()
        } catch (_: Throwable) {
        }
        thread?.let {
            if (it !== Thread.currentThread()) {
                try {
                    it.join(900)
                } catch (_: InterruptedException) {
                }
            }
        }
        try {
            audioRecord?.release()
        } catch (_: Throwable) {
        }
        record = null
        thread = null
        started = false
        val result = synchronized(lock) {
            val total = totalSamples
            if (total <= 0) {
                null
            } else {
                val samples = FloatArray(total)
                var at = 0
                for (chunk in chunks) {
                    for (sample in chunk) {
                        samples[at++] = sample / 32768f
                    }
                }
                val avg = if (levelCount > 0) (sumLevel / levelCount).toFloat() else 0f
                RecordingResult(samples, total * 1000L / sampleRate, lastPeak, avg)
            }
        }
        synchronized(lock) {
            chunks.clear()
            totalSamples = 0
        }
        return result
    }

    fun cancel() {
        running = false
        val audioRecord = record
        try {
            audioRecord?.stop()
        } catch (_: Throwable) {
        }
        thread?.let {
            try {
                it.join(600)
            } catch (_: InterruptedException) {
            }
        }
        try {
            audioRecord?.release()
        } catch (_: Throwable) {
        }
        record = null
        thread = null
        started = false
        synchronized(lock) {
            chunks.clear()
            totalSamples = 0
        }
    }

    fun isRecording(): Boolean = started
}
