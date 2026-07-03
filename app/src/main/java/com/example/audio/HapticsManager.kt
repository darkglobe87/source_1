package com.example.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Short haptic taps for key game moments (correct/wrong guess, win/loss, button
 * taps). Same singleton + SharedPreferences-persisted-toggle pattern as
 * MusicManager. Silently no-ops on devices/emulators without a vibrator.
 */
object HapticsManager {

    private const val PREFS = "audio_prefs"
    private const val KEY_ENABLED = "haptics_enabled"

    private var vibrator: Vibrator? = null
    private var appContext: Context? = null

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _enabled.value = prefs.getBoolean(KEY_ENABLED, true)
        vibrator = context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun toggleEnabled() {
        val newValue = !_enabled.value
        _enabled.value = newValue
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_ENABLED, newValue)?.apply()
    }

    fun tap() = vibrateOneShot(15)
    fun correct() = vibrateOneShot(25)
    fun wrong() = vibrateWaveform(longArrayOf(0, 30, 40, 30))
    fun win() = vibrateWaveform(longArrayOf(0, 25, 40, 25, 40, 60))
    fun lose() = vibrateOneShot(120)

    private fun vibrateOneShot(ms: Long) {
        if (!_enabled.value) return
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun vibrateWaveform(pattern: LongArray) {
        if (!_enabled.value) return
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
