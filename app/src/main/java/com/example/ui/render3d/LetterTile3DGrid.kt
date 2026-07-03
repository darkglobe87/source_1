package com.example.ui.render3d

import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.screens.splitIntoWrapSegments
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.NeonCyan
import kotlinx.coroutines.isActive

/**
 * Real 3D replacement for the old FlowRow-of-LetterBox layout: same word-wrap
 * behavior (via [layoutTitleAsTiles], ported from the original algorithm), but
 * every tile is drawn as a genuine lit, rotating box in a single shared GL
 * scene instead of a Compose graphicsLayer perspective fake.
 *
 * Deliberately out of scope for this pass: the keyboard/buttons/cards stay on
 * the existing lightweight pseudo-3D treatment, since those need reliable
 * touch input and giving each one its own real 3D pass is a separate,
 * larger effort.
 */
@Composable
fun LetterTile3DGrid(
    title: String,
    guessedLetters: Set<Char>,
    modifier: Modifier = Modifier,
    dangerLevel: Float = 0f,
    accentColor: Color = NeonCyan
) {
    val density = LocalDensity.current
    val context = LocalContext.current

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val words = title.split(" ")
        val longestSegmentLen = (words.flatMap { splitIntoWrapSegments(it) }.maxOfOrNull { it.length } ?: 1).coerceAtLeast(1)
        val letterSpacingDp = 4.dp
        val letterSpacingPx = with(density) { letterSpacingDp.toPx() }
        val dynamicSizeDp = ((maxWidth - letterSpacingDp * (longestSegmentLen - 1)) / longestSegmentLen).coerceIn(26.dp, 40.dp)
        val tileSizePx = with(density) { dynamicSizeDp.toPx() }.coerceAtLeast(1f)
        val wordGapPx = with(density) { 18.dp.toPx() }
        val rowSpacingPx = with(density) { 12.dp.toPx() }

        val layout = remember(title, maxWidthPx, tileSizePx) {
            layoutTitleAsTiles(title, maxWidthPx, tileSizePx, letterSpacingPx, wordGapPx)
        }

        val heightPx = if (layout.rowCount == 0) tileSizePx
        else layout.rowCount * tileSizePx + (layout.rowCount - 1) * rowSpacingPx
        val heightDp = with(density) { heightPx.toDp() }

        // One Animatable per placed tile. Snapshotting `guessedLetters` only at
        // `remember(title)` time means an already-revealed char (e.g. starting
        // punctuation) appears revealed with no flip, matching the original
        // LetterBox behavior; live reveals during play animate normally below.
        val flipAnims = remember(title) {
            layout.tiles.map { tile ->
                val initiallyRevealed = tile.char.uppercaseChar() in guessedLetters
                Animatable(if (initiallyRevealed) 180f else 0f)
            }
        }
        layout.tiles.forEachIndexed { index, tile ->
            val revealed = tile.char.uppercaseChar() in guessedLetters
            LaunchedEffect(title, index, revealed) {
                if (revealed && flipAnims[index].value < 179f) {
                    flipAnims[index].animateTo(180f, animationSpec = tween(380, easing = FastOutSlowInEasing))
                }
            }
        }

        val sceneState = remember { TileSceneState() }
        sceneState.tileSizePx = tileSizePx
        val hiddenColor = floatArrayOf(DarkSurfaceVariant.red, DarkSurfaceVariant.green, DarkSurfaceVariant.blue)

        LaunchedEffect(layout, tileSizePx) {
            val rowPitchUnits = 1f + (rowSpacingPx / tileSizePx)
            val totalHeightUnits = if (layout.rowCount > 1) (layout.rowCount - 1) * rowPitchUnits else 0f
            while (isActive) {
                withFrameNanos {
                    sceneState.tiles = layout.tiles.mapIndexed { index, tile ->
                        val xUnits = tile.xWorld / tileSizePx
                        val yUnits = totalHeightUnits / 2f - tile.row * rowPitchUnits
                        TileDrawInfo(xUnits, yUnits, flipAnims[index].value, tile.char)
                    }
                    sceneState.tileSizePx = tileSizePx
                    sceneState.dangerLevel = dangerLevel
                    sceneState.accentColor = floatArrayOf(accentColor.red, accentColor.green, accentColor.blue)
                }
            }
        }

        DisposableEffect(Unit) {
            val listener = TiltSensorListener { pitch, roll ->
                sceneState.tiltPitchDeg = pitch
                sceneState.tiltRollDeg = roll
            }
            val registered = TiltSensorListener.registerIfAvailable(context, listener)
            onDispose {
                if (registered) TiltSensorListener.unregister(context, listener)
            }
        }

        val glViewHolder = remember { mutableStateOf<GLSurfaceView?>(null) }
        DisposableEffect(Unit) {
            onDispose { glViewHolder.value?.onPause() }
        }

        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(2)
                    setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                    holder.setFormat(PixelFormat.TRANSLUCENT)
                    setZOrderOnTop(true)
                    setRenderer(LetterTileRenderer(sceneState, hiddenColor, floatArrayOf(accentColor.red, accentColor.green, accentColor.blue)))
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    glViewHolder.value = this
                }
            },
            modifier = Modifier.fillMaxWidth().height(heightDp)
        )
    }
}
