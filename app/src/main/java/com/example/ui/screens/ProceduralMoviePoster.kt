package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.example.ui.theme.CorrectGreen
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.RadioactiveGreen
import com.example.ui.theme.RadioactiveYellowGreen
import kotlin.random.Random

private val POSTER_PALETTE = listOf(NeonCyan, NeonPink, NeonPurple, RadioactiveGreen, RadioactiveYellowGreen, CorrectGreen)

private data class PosterPlan(val template: Int, val colorA: Color, val colorB: Color, val colorC: Color, val seedFloats: List<Float>)

private fun planFor(title: String): PosterPlan {
    val rnd = Random(title.hashCode())
    val shuffled = POSTER_PALETTE.shuffled(rnd)
    return PosterPlan(
        template = rnd.nextInt(5),
        colorA = shuffled[0],
        colorB = shuffled[1],
        colorC = shuffled[2],
        seedFloats = List(9) { rnd.nextFloat() }
    )
}

/**
 * A deterministic, title-seeded abstract "poster" for movies without dedicated
 * artwork - the same movie always produces the same look, picked from a small
 * set of pre-designed compositions/colors so it stays cohesive with the app's
 * theme rather than looking randomly thrown together. Purely geometric - no
 * depiction of any real imagery - used as the fallback for movies that don't
 * have a specific drawable wired up.
 */
@Composable
fun ProceduralMoviePoster(title: String, modifier: Modifier = Modifier) {
    val plan = remember(title) { planFor(title) }
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val f = plan.seedFloats
        drawRect(color = DarkSurface)

        when (plan.template) {
            0 -> { // Concentric rings
                val cx = w * (0.35f + f[0] * 0.3f)
                val cy = h * 0.5f
                val maxR = minOf(w, h * 2f) * 0.42f
                val colors = listOf(plan.colorA, plan.colorB, plan.colorC)
                for (i in 0 until 4) {
                    val r = maxR * (1f - i * 0.22f)
                    drawCircle(color = colors[i % colors.size].copy(alpha = 0.85f), radius = r, center = Offset(cx, cy))
                }
            }
            1 -> { // Diagonal stripes
                val stripeCount = 5
                val stripeW = w / stripeCount * 1.4f
                for (i in -1..stripeCount) {
                    val x = i * (w / stripeCount)
                    val color = if (i % 2 == 0) plan.colorA else plan.colorB
                    val path = Path().apply {
                        moveTo(x, 0f)
                        lineTo(x + stripeW, 0f)
                        lineTo(x + stripeW - h * 0.5f, h)
                        lineTo(x - h * 0.5f, h)
                        close()
                    }
                    drawPath(path, color = color.copy(alpha = 0.55f))
                }
            }
            2 -> { // Scattered circles
                for (i in 0 until 8) {
                    val cx = w * f[i % f.size]
                    val cy = h * f[(i * 3) % f.size]
                    val r = minOf(w, h) * (0.06f + 0.16f * f[(i + 2) % f.size])
                    val color = listOf(plan.colorA, plan.colorB, plan.colorC)[i % 3]
                    drawCircle(color = color.copy(alpha = 0.55f), radius = r, center = Offset(cx, cy))
                }
            }
            3 -> { // Triangle cluster
                val triangles = listOf(plan.colorA, plan.colorB, plan.colorC)
                for (i in 0 until 3) {
                    val baseX = w * (0.15f + i * 0.3f + f[i] * 0.1f)
                    val baseY = h * (0.85f + f[i + 3] * 0.1f)
                    val height = h * (0.5f + f[i + 1] * 0.3f)
                    val width = w * (0.25f + f[i + 2] * 0.15f)
                    val path = Path().apply {
                        moveTo(baseX, baseY)
                        lineTo(baseX + width / 2f, baseY - height)
                        lineTo(baseX + width, baseY)
                        close()
                    }
                    drawPath(path, color = triangles[i].copy(alpha = 0.75f))
                }
            }
            else -> { // Radial burst
                val cx = w * 0.5f
                val cy = h * 0.5f
                drawCircle(color = plan.colorA.copy(alpha = 0.9f), radius = minOf(w, h) * 0.14f, center = Offset(cx, cy))
                val rayCount = 10
                val innerR = minOf(w, h) * 0.18f
                val outerR = minOf(w, h) * 0.62f
                val spread = 0.12f
                for (i in 0 until rayCount) {
                    val angle = (i.toFloat() / rayCount) * 2f * Math.PI.toFloat() + f[i % f.size]
                    val x1 = cx + kotlin.math.cos(angle - spread) * innerR
                    val y1 = cy + kotlin.math.sin(angle - spread) * innerR
                    val x2 = cx + kotlin.math.cos(angle + spread) * innerR
                    val y2 = cy + kotlin.math.sin(angle + spread) * innerR
                    val x3 = cx + kotlin.math.cos(angle) * outerR
                    val y3 = cy + kotlin.math.sin(angle) * outerR
                    val path = Path().apply {
                        moveTo(x1, y1)
                        lineTo(x3, y3)
                        lineTo(x2, y2)
                        close()
                    }
                    val color = if (i % 2 == 0) plan.colorB else plan.colorC
                    drawPath(path, color = color.copy(alpha = 0.5f))
                }
            }
        }
    }
}
