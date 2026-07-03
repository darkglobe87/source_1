package com.example.ui.render3d

import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.NeonCyan
import kotlinx.coroutines.isActive

/**
 * The main menu's title + PLAY + STORE, rendered as real lit 3D objects (see
 * MenuSceneRenderer) instead of the graphicsLayer tilt trick previously used
 * for the title. Touch input deliberately stays in Compose: invisible
 * clickable boxes are positioned to match where the 3D buttons render, so
 * tapping doesn't depend on any 3D hit-testing.
 */
@Composable
fun Menu3DScene(
    onPlay: () -> Unit,
    onNavigateToStore: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = NeonCyan
) {
    val sceneState = remember { MenuSceneState() }
    val hiddenColor = floatArrayOf(DarkSurfaceVariant.red, DarkSurfaceVariant.green, DarkSurfaceVariant.blue)
    val accent = floatArrayOf(accentColor.red, accentColor.green, accentColor.blue)

    val playInteraction = remember { MutableInteractionSource() }
    val storeInteraction = remember { MutableInteractionSource() }
    val playPressed by playInteraction.collectIsPressedAsState()
    val storePressed by storeInteraction.collectIsPressedAsState()

    val playPress = remember { Animatable(0f) }
    val storePress = remember { Animatable(0f) }
    LaunchedEffect(playPressed) {
        playPress.animateTo(if (playPressed) 1f else 0f, animationSpec = tween(if (playPressed) 80 else 180))
    }
    LaunchedEffect(storePressed) {
        storePress.animateTo(if (storePressed) 1f else 0f, animationSpec = tween(if (storePressed) 80 else 180))
    }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now ->
                sceneState.elapsedSec = (now - start) / 1_000_000_000f
                sceneState.playPressProgress = playPress.value
                sceneState.storePressProgress = storePress.value
            }
        }
    }

    val glViewHolder = remember { mutableStateOf<GLSurfaceView?>(null) }
    DisposableEffect(Unit) {
        onDispose { glViewHolder.value?.onPause() }
    }

    val referenceDp = MenuSceneLayout.referenceDp
    val totalWidthDp = MenuSceneLayout.totalWidthDp.dp
    val totalHeightDp = MenuSceneLayout.totalHeightDp.dp

    fun topOffsetFor(centerY: Float, halfHeight: Float): Dp =
        ((MenuSceneLayout.halfHeightUnits - (centerY + halfHeight)) * referenceDp).dp

    Box(modifier = modifier.width(totalWidthDp).height(totalHeightDp)) {
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(2)
                    setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                    holder.setFormat(PixelFormat.TRANSLUCENT)
                    setZOrderOnTop(true)
                    setRenderer(MenuSceneRenderer(sceneState, hiddenColor, accent))
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    glViewHolder.value = this
                }
            },
            modifier = Modifier.width(totalWidthDp).height(totalHeightDp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = topOffsetFor(MenuSceneLayout.playCenterY, MenuSceneLayout.playHalfH))
                .width((MenuSceneLayout.playHalfW * 2 * referenceDp).dp)
                .height((MenuSceneLayout.playHalfH * 2 * referenceDp).dp)
                .clickable(interactionSource = playInteraction, indication = null, onClick = onPlay)
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = topOffsetFor(MenuSceneLayout.storeCenterY, MenuSceneLayout.storeHalfH))
                .width((MenuSceneLayout.storeHalfW * 2 * referenceDp).dp)
                .height((MenuSceneLayout.storeHalfH * 2 * referenceDp).dp)
                .clickable(interactionSource = storeInteraction, indication = null, onClick = onNavigateToStore)
        )
    }
}
