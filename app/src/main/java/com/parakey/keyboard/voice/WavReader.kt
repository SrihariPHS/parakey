package com.parakey.keyboard.voice

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

data class WavData(
    val samples: FloatArray,
    val sampleRate: Int,
) {
    val durationMs: Long
        get() = if (sampleRate <= 0) 0L else samples.size * 1000L / sampleRate
}

/**
 * Small, dependency-free WAV reader. It accepts PCM 8/16/24/32-bit and IEEE
 * float 32-bit files, mixes stereo to mono, and resamples to 16 kHz.
 */
object WavReader {

    private const val TARGET_RATE = 16_000
    private const val MAX_SAMPLES = 16_000 * 60 * 10

    fun read(file: File): WavData = read(file.readBytes())

    fun read(bytes: ByteArray): WavData {
        require(bytes.size >= 44) { "Not a WAV file: too short" }
        require(String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF") { "Not a RIFF file" }
        require(String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE") { "Not a WAVE file" }

        var offset = 12
        var fmt: Fmt? = null
        var dataOffset = -1
        var dataSize = -1

        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = readUInt32LE(bytes, offset + 4)
            val body = offset + 8
            if (size < 0 || body + size > bytes.size) {
                // Some encoders write a bogus size for the last chunk. Clamp it.
                if (id == "data") {
                    dataOffset = body
                    dataSize = bytes.size - body
                }
                break
            }
            when (id) {
                "fmt " -> fmt = parseFmt(bytes, body, size)
                "data" -> {
                    dataOffset = body
                    dataSize = size
                }
            }
            offset = body + size + (size and 1)
        }

        val format = fmt ?: error("WAV is missing a fmt chunk")
        require(dataOffset >= 0 && dataSize > 0) { "WAV is missing audio data" }

        val channels = format.channels.coerceAtLeast(1)
        val sampleRate = format.sampleRate
        val bits = format.bitsPerSample
        val formatCode = format.formatCode
        val bytesPerSample = (bits + 7) / 8
        val frameBytes = bytesPerSample * channels
        require(frameBytes > 0) { "Invalid WAV frame size" }
        val frames = (dataSize / frameBytes).coerceAtMost(MAX_SAMPLES)
        val mono = FloatArray(frames)

        val buffer = ByteBuffer.wrap(bytes, dataOffset, frames * frameBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) {
                sum += when {
                    formatCode == 3 && bits == 32 -> buffer.float
                    formatCode == 1 && bits == 8 -> ((buffer.get().toInt() and 0xFF) - 128) / 128f
                    formatCode == 1 && bits == 16 -> buffer.short / 32768f
                    formatCode == 1 && bits == 24 -> read24(buffer) / 8388608f
                    formatCode == 1 && bits == 32 -> buffer.int / 2147483648f
                    else -> error("Unsupported WAV encoding: format=$formatCode bits=$bits")
                }
            }
            mono[i] = (sum / channels).coerceIn(-1f, 1f)
        }

        val resampled = if (sampleRate == TARGET_RATE) mono else resample(mono, sampleRate, TARGET_RATE)
        return WavData(resampled, TARGET_RATE)
    }

    private data class Fmt(
        val formatCode: Int,
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
    )

    private fun parseFmt(bytes: ByteArray, offset: Int, size: Int): Fmt {
        require(size >= 16) { "WAV fmt chunk is too short" }
        var formatCode = readUInt16LE(bytes, offset)
        val channels = readUInt16LE(bytes, offset + 2)
        val sampleRate = readUInt32LE(bytes, offset + 4)
        val bits = readUInt16LE(bytes, offset + 14)
        if (formatCode == 0xFFFE && size >= 40) {
            // WAVE_FORMAT_EXTENSIBLE uses the first two bytes of SubFormat GUID.
            formatCode = readUInt16LE(bytes, offset + 24)
        }
        return Fmt(formatCode, channels, sampleRate, bits)
    }

    private fun read24(buffer: ByteBuffer): Int {
        val b0 = buffer.get().toInt() and 0xFF
        val b1 = buffer.get().toInt() and 0xFF
        val b2 = buffer.get().toInt() and 0xFF
        var value = b0 or (b1 shl 8) or (b2 shl 16)
        if (value and 0x800000 != 0) value = value or -0x1000000
        return value
    }

    private fun resample(input: FloatArray, from: Int, to: Int): FloatArray {
        if (from <= 0 || input.isEmpty()) return FloatArray(0)
        val outCount = (input.size.toLong() * to / from).toInt().coerceAtLeast(1)
        val output = FloatArray(outCount)
        val ratio = from.toDouble() / to.toDouble()
        for (i in 0 until outCount) {
            val source = i * ratio
            val i0 = source.toInt().coerceIn(0, input.size - 1)
            val i1 = (i0 + 1).coerceAtMost(input.size - 1)
            val frac = (source - i0).toFloat()
            output[i] = input[i0] * (1f - frac) + input[i1] * frac
        }
        return output
    }

    private fun readUInt16LE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun readUInt32LE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    fun encodePcm16(samples: FloatArray, sampleRate: Int = TARGET_RATE): ByteArray {
        val dataSize = samples.size * 2
        val out = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray(Charsets.US_ASCII))
        out.putInt(36 + dataSize)
        out.put("WAVE".toByteArray(Charsets.US_ASCII))
        out.put("fmt ".toByteArray(Charsets.US_ASCII))
        out.putInt(16)
        out.putShort(1)
        out.putShort(1)
        out.putInt(sampleRate)
        out.putInt(sampleRate * 2)
        out.putShort(2)
        out.putShort(16)
        out.put("data".toByteArray(Charsets.US_ASCII))
        out.putInt(dataSize)
        for (sample in samples) {
            val value = (sample.coerceIn(-1f, 1f) * 32767f).roundToInt()
            out.putShort(value.toShort())
        }
        return out.array()
    }
}
