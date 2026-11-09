package com.parakey.keyboard

import com.parakey.keyboard.voice.WavReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class WavReaderTest {

    @Test
    fun readsSixteenBitMono() {
        val samples = FloatArray(1_600) { i -> sin(i * 0.05).toFloat() * 0.5f }
        val encoded = WavReader.encodePcm16(samples, 16_000)
        val decoded = WavReader.read(encoded)
        assertEquals(16_000, decoded.sampleRate)
        assertEquals(samples.size, decoded.samples.size)
        assertTrue(kotlin.math.abs(decoded.samples[10] - samples[10]) < 0.001f)
    }

    @Test
    fun resamplesFortyEightKilohertz() {
        val samples = FloatArray(4_800) { i -> sin(i * 0.02).toFloat() }
        val encoded = WavReader.encodePcm16(samples, 48_000)
        val decoded = WavReader.read(encoded)
        assertEquals(16_000, decoded.sampleRate)
        assertTrue(decoded.samples.size in 1_590..1_610)
    }
}
