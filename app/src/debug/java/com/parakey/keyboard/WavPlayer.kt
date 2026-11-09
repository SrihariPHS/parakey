package com.parakey.keyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.parakey.keyboard.voice.WavReader
import java.io.File
import kotlin.math.roundToInt

/** Plays a 16 kHz WAV through the media speaker for acoustic loopback tests. */
object WavPlayer {

    @Volatile
    var isPlaying: Boolean = false
        private set

    fun play(context: Context, file: File, onDone: (() -> Unit)? = null) {
        if (isPlaying) return
        isPlaying = true
        Thread({
            var track: AudioTrack? = null
            try {
                val wav = WavReader.read(file)
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager?.let { manager ->
                    val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    manager.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
                }
                val shorts = ShortArray(wav.samples.size) { i ->
                    (wav.samples[i].coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
                }
                val minBuffer = AudioTrack.getMinBufferSize(
                    wav.sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
                val bufferSize = maxOf(minBuffer, wav.sampleRate * 2)
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(wav.sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track.play()
                var offset = 0
                while (offset < shorts.size) {
                    val count = minOf(2048, shorts.size - offset)
                    val written = track.write(shorts, offset, count)
                    if (written <= 0) break
                    offset += written
                }
                track.stop()
            } catch (_: Throwable) {
            } finally {
                try {
                    track?.release()
                } catch (_: Throwable) {
                }
                isPlaying = false
                onDone?.invoke()
            }
        }, "parakey-wav-player").start()
    }
}
