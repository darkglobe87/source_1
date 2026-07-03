package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the looping background-music MediaPlayer for the whole app.
 *
 * Lifecycle rules:
 *  - start()/pause() are driven by the Activity lifecycle (see MainActivity) so
 *    music stops when the app is backgrounded and resumes when it returns.
 *  - Mute is a persisted user preference (SharedPreferences). When muted we keep
 *    the player "playing" at zero volume rather than stopping it, so toggling mute
 *    back on is instant and stays in sync with the loop position.
 *
 * Expects a looping audio file at res/raw/bg_music (e.g. bg_music.mp3 or .ogg).
 */
object MusicManager {

    private const val PREFS = "audio_prefs"
    private const val KEY_MUTED = "music_muted"

    private var player: MediaPlayer? = null
    private var appContext: Context? = null
    private var shouldPlay = false // reflects foreground state

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _isMuted.value = prefs.getBoolean(KEY_MUTED, false)
    }

    /** Call from the Activity's onStart/onResume. Safe to call repeatedly. */
    fun start() {
        shouldPlay = true
        val ctx = appContext ?: return
        if (player == null) {
            try {
                // Resolve res/raw/bg_music.* by name so the project still compiles
                // if the audio file hasn't been added yet (returns 0 when absent).
                val resId = ctx.resources.getIdentifier("bg_music", "raw", ctx.packageName)
                if (resId == 0) return // no music file present - run silently
                player = MediaPlayer.create(ctx, resId)?.apply {
                    isLooping = true
                    applyVolume(this)
                }
            } catch (e: Exception) {
                // If the resource is missing or fails to decode, fail silently -
                // the game must still run without music.
                e.printStackTrace()
                player = null
                return
            }
        }
        player?.let { p ->
            if (!p.isPlaying) {
                try { p.start() } catch (e: IllegalStateException) { e.printStackTrace() }
            }
        }
    }

    /** Call from the Activity's onStop/onPause. */
    fun pause() {
        shouldPlay = false
        player?.let { p ->
            try { if (p.isPlaying) p.pause() } catch (e: IllegalStateException) { e.printStackTrace() }
        }
    }

    fun toggleMute() {
        val newMuted = !_isMuted.value
        _isMuted.value = newMuted
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_MUTED, newMuted)?.apply()
        player?.let { applyVolume(it) }
    }

    private fun applyVolume(p: MediaPlayer) {
        val v = if (_isMuted.value) 0f else 0.5f
        try { p.setVolume(v, v) } catch (e: IllegalStateException) { e.printStackTrace() }
    }

    /** Call from the Activity's onDestroy to release native resources. */
    fun release() {
        player?.let { p ->
            try { p.stop() } catch (e: IllegalStateException) { e.printStackTrace() }
            p.release()
        }
        player = null
    }
}
