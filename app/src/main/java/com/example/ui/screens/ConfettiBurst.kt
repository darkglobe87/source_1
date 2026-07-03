package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.RadioactiveGreen
import kotlinx.coroutines.isActive
import kotlin.random.Random

private data class ConfettiPiece(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val rotation: Float,
    val rotationSpeed: Float,
    val color: Color,
    val widthDp: Float,
    val heightDp: Float,
    val wobblePhase: Float
)

private const val BURST_DURATION_SEC = 3.2f
private const val FADE_START_SEC = 2.4f

/**
 * A full-screen confetti burst for level-complete fanfare. Fires once each time
 * [trigger] changes to a non-zero value (bump a counter on win), runs for a
 * few seconds, then clears itself - not a persistent background effect.
 */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier) {
    var pieces by remember { mutableStateOf(emptyList<ConfettiPiece>()) }
    var elapsedSec by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect

        val palette = listOf(NeonCyan, NeonPink, NeonPurple, RadioactiveGreen, Color(0xFFFFD700))
        var current = List(140) {
            ConfettiPiece(
                x = Random.nextFloat(),
                y = -0.15f - Random.nextFloat() * 0.5f,
                vx = (Random.nextFloat() - 0.5f) * 0.25f,
                vy = 0.28f + Random.nextFloat() * 0.35f,
                rotation = Random.nextFloat() * 360f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 420f,
                color = palette.random(),
                widthDp = 6f + Random.nextFloat() * 6f,
                heightDp = 10f + Random.nextFloat() * 8f,
                wobblePhase = Random.nextFloat() * 6.283f
            )
        }
        pieces = current
        elapsedSec = 0f

        var lastTime = withFrameNanos { it }
        while (isActive && elapsedSec < BURST_DURATION_SEC) {
            withFrameNanos { now ->
                val dt = (now - lastTime) / 1_000_000_000f
                lastTime = now
                elapsedSec += dt
                current = current.map { p ->
                    p.copy(
                        x = p.x + (p.vx + 0.06f * kotlin.math.sin(elapsedSec * 2f + p.wobblePhase)) * dt,
                        y = p.y + p.vy * dt,
                        rotation = p.rotation + p.rotationSpeed * dt
                    )
                }
                pieces = current
            }
        }
        pieces = emptyList()
    }

    if (pieces.isNotEmpty()) {
        val alpha = if (elapsedSec > FADE_START_SEC) {
            (1f - (elapsedSec - FADE_START_SEC) / (BURST_DURATION_SEC - FADE_START_SEC)).coerceIn(0f, 1f)
        } else 1f

        Canvas(modifier = modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            for (p in pieces) {
                val cx = p.x * w
                val cy = p.y * h
                if (cy < -50f || cy > h + 50f) continue
                val pieceW = p.widthDp.dp.toPx()
                val pieceH = p.heightDp.dp.toPx()
                rotate(degrees = p.rotation, pivot = Offset(cx, cy)) {
                    drawRect(
                        color = p.color.copy(alpha = alpha),
                        topLeft = Offset(cx - pieceW / 2f, cy - pieceH / 2f),
                        size = Size(pieceW, pieceH)
                    )
                }
            }
        }
    }
}
