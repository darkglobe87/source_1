package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.sin

/**
 * Short procedurally-synthesized sound effects (correct/wrong guess, win, lose,
 * button tap) - there are no audio asset files to load, so each sound is a
 * generated PCM tone rather than a decoded file. Same singleton +
 * SharedPreferences-persisted-toggle pattern as MusicManager.
 */
object SfxManager {

    private const val PREFS = "audio_prefs"
    private const val KEY_ENABLED = "sfx_enabled"
    private const val SAMPLE_RATE = 22050

    private var appContext: Context? = null

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private var correctTrack: AudioTrack? = null
    private var wrongTrack: AudioTrack? = null
    private var winTrack: AudioTrack? = null
    private var loseTrack: AudioTrack? = null
    private var tapTrack: AudioTrack? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _enabled.value = prefs.getBoolean(KEY_ENABLED, true)

        if (correctTrack == null) {
            correctTrack = buildTrack(tone(120, 620f, 920f, amplitude = 0.5f))
            wrongTrack = buildTrack(tone(160, 340f, 200f, amplitude = 0.55f))
            tapTrack = buildTrack(tone(35, 500f, 500f, amplitude = 0.3f))
            winTrack = buildTrack(
                concatTones(
                    listOf(
                        tone(110, 523f, 523f, amplitude = 0.5f),
                        tone(110, 659f, 659f, amplitude = 0.5f),
                        tone(180, 784f, 784f, amplitude = 0.5f)
                    )
                )
            )
            loseTrack = buildTrack(
                concatTones(
                    listOf(
                        tone(140, 392f, 392f, amplitude = 0.5f),
                        tone(140, 330f, 330f, amplitude = 0.5f),
                        tone(240, 261f, 261f, amplitude = 0.5f)
                    )
                )
            )
        }
    }

    fun toggleEnabled() {
        val newValue = !_enabled.value
        _enabled.value = newValue
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_ENABLED, newValue)?.apply()
    }

    fun playCorrect() = play(correctTrack)
    fun playWrong() = play(wrongTrack)
    fun playWin() = play(winTrack)
    fun playLose() = play(loseTrack)
    fun playTap() = play(tapTrack)

    private fun play(track: AudioTrack?) {
        if (!_enabled.value || track == null) return
        try {
            track.stop()
            track.reloadStaticData()
            track.play()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }

    /** Call from the Activity's onDestroy to release native resources. */
    fun release() {
        correctTrack?.release(); wrongTrack?.release(); winTrack?.release(); loseTrack?.release(); tapTrack?.release()
        correctTrack = null; wrongTrack = null; winTrack = null; loseTrack = null; tapTrack = null
    }

    /** A short sine tone, sweeping from freqStart to freqEnd, with a brief attack/release envelope to avoid clicks. */
    private fun tone(durationMs: Int, freqStart: Float, freqEnd: Float, amplitude: Float): ShortArray {
        val numSamples = (SAMPLE_RATE * durationMs / 1000f).toInt().coerceAtLeast(1)
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i / SAMPLE_RATE.toFloat()
            val progress = i / numSamples.toFloat()
            val freq = freqStart + (freqEnd - freqStart) * progress
            val envelope = when {
                progress < 0.1f -> progress / 0.1f
                progress > 0.75f -> ((1f - progress) / 0.25f).coerceIn(0f, 1f)
                else -> 1f
            }
            val sample = sin(2.0 * PI * freq * t).toFloat() * amplitude * envelope
            buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return buffer
    }

    private fun concatTones(tones: List<ShortArray>): ShortArray {
        val total = tones.sumOf { it.size }
        val out = ShortArray(total)
        var offset = 0
        for (t in tones) {
            t.copyInto(out, offset)
            offset += t.size
        }
        return out
    }

    private fun buildTrack(samples: ShortArray): AudioTrack? {
        return try {
            val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val bufferSize = maxOf(samples.size * 2, minBuf)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(samples, 0, samples.size)
            track
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
