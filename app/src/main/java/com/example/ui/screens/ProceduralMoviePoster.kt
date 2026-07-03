package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.ui.theme.CorrectGreen
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.RadioactiveGreen
import com.example.ui.theme.RadioactiveYellowGreen
import kotlin.random.Random

private val POSTER_PALETTE = listOf(NeonCyan, NeonPink, NeonPurple, RadioactiveGreen, RadioactiveYellowGreen, CorrectGreen)

private enum class Mood { HORROR, CRIME, SCIFI, ACTION, FANTASY, FAMILY, ROMANCE }

// Calibrated by hand against the actual 36 entries in movies.json - not a guess.
// Scores by keyword-hit count across title + description + all three hints, so
// it should degrade gracefully (falls back to the generic abstract look below)
// for any future/AI-generated movie whose text doesn't match these words.
private val MOOD_KEYWORDS: Map<Mood, List<String>> = mapOf(
    Mood.HORROR to listOf("homicidal", "blood", "haunted", "demon", "possessed", "monster", "scream", "terror", "nightmare", "dead people", "chestburster", "kills", "murder", "evil", "elevator of blood"),
    Mood.CRIME to listOf("crime family", "mafia", "hitmen", "heist", "gang", "mob", "offer he can't refuse", "horse head"),
    Mood.SCIFI to listOf("space", "alien", "robot", "spaceship", "laser", "simulation", "hacker", "time travel", "gigawatts", "skynet", "unobtanium", "phone home", "government agents", "tars", "death star"),
    Mood.ACTION to listOf("cop", "vigilante", "grappling hook", "boxer", "boxing", "fight", "explosion", "rubber suit", "joker", "ventilation ducts", "yippee"),
    Mood.FANTASY to listOf("wizard", "kingdom", "curse", "cursed", "castle", "ring", "dragon", "prince", "princess", "magic", "quest", "jewelry", "onion", "mount doom"),
    Mood.FAMILY to listOf("toy", "cartoon", "meerkat", "warthog", "ice powers", "coronation", "dinosaur", "petting zoo", "clownfish", "toys come alive", "pride rock"),
    Mood.ROMANCE to listOf("romance", "love", "wedding", "musical", "stockholm syndrome", "greaser", "clique", "popular", "burn book", "romantic advances", "king of the world")
)

private class MoodStyle(val colorA: Color, val colorB: Color, val colorC: Color, val background: Color)

private fun styleFor(mood: Mood): MoodStyle = when (mood) {
    Mood.HORROR -> MoodStyle(ErrorRed, Color(0xFFB00020), Color(0xFF3B0A0A), Color(0xFF120505))
    Mood.CRIME -> MoodStyle(Color(0xFFFFD700), Color(0xFFB8960A), Color(0xFF1A1410), Color(0xFF0A0A10))
    Mood.SCIFI -> MoodStyle(NeonCyan, NeonPurple, Color(0xFF0B3B52), Color(0xFF04101A))
    Mood.ACTION -> MoodStyle(Color(0xFFFF6A00), ErrorRed, Color(0xFFFFC24A), Color(0xFF1A0A05))
    Mood.FANTASY -> MoodStyle(NeonPurple, Color(0xFFFFD700), Color(0xFF2A2A3D), Color(0xFF0A0A10))
    Mood.FAMILY -> MoodStyle(Color(0xFFFFD166), NeonPink, RadioactiveYellowGreen, Color(0xFF1A160A))
    Mood.ROMANCE -> MoodStyle(NeonPink, Color(0xFFFF8FB3), NeonPurple, Color(0xFF1A0A14))
}

private fun detectMood(text: String): Mood? {
    val lower = text.lowercase()
    var best: Mood? = null
    var bestScore = 0
    for ((mood, words) in MOOD_KEYWORDS) {
        val score = words.count { lower.contains(it) }
        if (score > bestScore) {
            bestScore = score
            best = mood
        }
    }
    return best
}

/**
 * A cursor over a precomputed pool of independent random floats, consumed
 * sequentially and never re-indexed via modulo - the previous version reused
 * a 9-float pool via expressions like `f[(i * 3) % f.size]`, which for
 * size=9/stride=3 only ever yields 3 distinct values (gcd(3,9)=3), causing
 * every shape to collapse onto the same few positions instead of spreading
 * across the canvas. Always pulling the *next* untouched value avoids that
 * whole bug class.
 */
private class FloatCursor(private val values: List<Float>) {
    private var index = 0
    fun next(): Float {
        val v = values[index % values.size]
        index++
        return v
    }
}

private data class PosterPlan(val mood: Mood?, val template: Int, val colorA: Color, val colorB: Color, val colorC: Color, val randomPool: List<Float>)

private fun planFor(title: String, contentText: String): PosterPlan {
    val rnd = Random(title.hashCode())
    val mood = detectMood(contentText)
    val (colorA, colorB, colorC) = if (mood != null) {
        val style = styleFor(mood)
        Triple(style.colorA, style.colorB, style.colorC)
    } else {
        val shuffled = POSTER_PALETTE.shuffled(rnd)
        Triple(shuffled[0], shuffled[1], shuffled[2])
    }
    return PosterPlan(
        mood = mood,
        template = rnd.nextInt(5),
        colorA = colorA,
        colorB = colorB,
        colorC = colorC,
        // Generous pool - the hungriest template (scattered circles) only ever
        // consumes 24 of these, so a fresh value is always available.
        randomPool = List(32) { rnd.nextFloat() }
    )
}

/**
 * A deterministic "poster" for movies without dedicated artwork - the same
 * movie always produces the same look. Priority order:
 *  1. A hand-designed icon tied to *that specific film* (see
 *     [drawSpecificIcon]), built from its own plot/scene/character hints -
 *     e.g. a Death Star + crossed lightsabers for Star Wars, a spinning top
 *     for Inception. This is what actually represents the film, not just its
 *     genre.
 *  2. If no specific icon exists but a mood/genre is detected from the text,
 *     a genre-appropriate composition and palette (dark reds for horror,
 *     cool cyan/purple for sci-fi, etc).
 *  3. Otherwise a generic abstract look seeded by the title, so every movie
 *     still gets *something* even if it matches neither of the above.
 * Purely geometric/symbolic - no depiction of copyrighted character art.
 */
@Composable
fun ProceduralMoviePoster(title: String, contentText: String, modifier: Modifier = Modifier) {
    val plan = remember(title, contentText) { planFor(title, contentText) }
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pool = FloatCursor(plan.randomPool)
        val background = plan.mood?.let { styleFor(it).background } ?: DarkSurface

        drawRect(color = background)
        val drewSpecific = drawSpecificIcon(title, w, h, plan.colorA, plan.colorB, plan.colorC)
        if (!drewSpecific) {
            if (plan.mood != null) {
                drawMoodPoster(plan.mood, plan.colorA, plan.colorB, plan.colorC, w, h)
            } else {
                drawGenericPoster(plan.template, plan.colorA, plan.colorB, plan.colorC, w, h, pool)
            }
        }
    }
}

/**
 * Hand-designed icon per named movie, built from that film's own plotHint/
 * sceneHint/characterHint in movies.json (Death Star, spinning top, DeLorean
 * lightning, etc.) - this is the part that ties a poster to *that specific
 * film* rather than just a genre. Returns false (drawing nothing) for movies
 * not covered here, so the caller falls back to the mood/generic look.
 */
private fun DrawScope.drawSpecificIcon(title: String, w: Float, h: Float, colorA: Color, colorB: Color, colorC: Color): Boolean {
    when (title) {
        "The Wizard of Oz" -> {
            val cx = w * 0.5f
            for (i in 0 until 5) {
                val yFrac = 0.2f + i * 0.14f
                val rad = minOf(w, h) * (0.05f + i * 0.06f)
                drawCircle(color = colorA.copy(alpha = 0.3f + i * 0.1f), radius = rad, center = Offset(cx, h * yFrac), style = Stroke(width = minOf(w, h) * 0.015f))
            }
            drawCircle(color = Color(0xFFB00020), radius = minOf(w, h) * 0.035f, center = Offset(w * 0.5f, h * 0.85f))
        }
        "Batman", "The Dark Knight" -> {
            drawCircle(color = Color(0xFFFFD700).copy(alpha = 0.25f), radius = minOf(w, h) * 0.5f, center = Offset(w * 0.5f, h * 0.3f))
            val earL = Path().apply { moveTo(w * 0.42f, h * 0.55f); lineTo(w * 0.46f, h * 0.3f); lineTo(w * 0.5f, h * 0.55f); close() }
            val earR = Path().apply { moveTo(w * 0.5f, h * 0.55f); lineTo(w * 0.54f, h * 0.3f); lineTo(w * 0.58f, h * 0.55f); close() }
            drawPath(earL, color = Color.Black)
            drawPath(earR, color = Color.Black)
            drawCircle(color = Color.Black, radius = minOf(w, h) * 0.16f, center = Offset(w * 0.5f, h * 0.62f))
        }
        "The Shining" -> {
            drawRect(color = colorC, topLeft = Offset(w * 0.25f, h * 0.3f), size = Size(w * 0.5f, h * 0.6f))
            val blood = Path().apply {
                moveTo(w * 0.45f, h * 0.3f); lineTo(w * 0.4f, h * 0.55f); lineTo(w * 0.5f, h * 0.5f)
                lineTo(w * 0.45f, h * 0.9f); lineTo(w * 0.55f, h * 0.9f); lineTo(w * 0.6f, h * 0.5f)
                lineTo(w * 0.5f, h * 0.55f); lineTo(w * 0.55f, h * 0.3f); close()
            }
            drawPath(blood, color = ErrorRed.copy(alpha = 0.85f))
        }
        "Star Wars" -> {
            drawLine(color = colorA, start = Offset(w * 0.25f, h * 0.75f), end = Offset(w * 0.6f, h * 0.2f), strokeWidth = minOf(w, h) * 0.03f)
            drawLine(color = ErrorRed, start = Offset(w * 0.75f, h * 0.75f), end = Offset(w * 0.4f, h * 0.2f), strokeWidth = minOf(w, h) * 0.03f)
            drawCircle(color = colorC.copy(alpha = 0.8f), radius = minOf(w, h) * 0.12f, center = Offset(w * 0.82f, h * 0.25f))
            drawCircle(color = DarkSurface, radius = minOf(w, h) * 0.035f, center = Offset(w * 0.86f, h * 0.22f))
        }
        "Titanic" -> {
            val waveBand = Path().apply { moveTo(0f, h * 0.75f); lineTo(w, h * 0.7f); lineTo(w, h); lineTo(0f, h); close() }
            drawPath(waveBand, color = Color(0xFF0B3B52))
            val bow = Path().apply { moveTo(w * 0.1f, h * 0.72f); lineTo(w * 0.6f, h * 0.72f); lineTo(w * 0.75f, h * 0.5f); lineTo(w * 0.15f, h * 0.5f); close() }
            drawPath(bow, color = colorC)
            val iceberg = Path().apply { moveTo(w * 0.55f, h * 0.72f); lineTo(w * 0.72f, h * 0.35f); lineTo(w * 0.92f, h * 0.72f); close() }
            drawPath(iceberg, color = Color.White.copy(alpha = 0.85f))
        }
        "The Matrix" -> {
            for (col in 0 until 6) {
                val x = w * (0.08f + col * 0.15f)
                for (row in 0 until 5) {
                    val y = h * (0.08f + row * 0.14f)
                    drawRect(color = RadioactiveGreen.copy(alpha = 0.5f + 0.08f * row), topLeft = Offset(x, y), size = Size(w * 0.03f, h * 0.05f))
                }
            }
            drawCircle(color = ErrorRed, radius = minOf(w, h) * 0.07f, center = Offset(w * 0.4f, h * 0.85f))
            drawCircle(color = NeonCyan, radius = minOf(w, h) * 0.07f, center = Offset(w * 0.58f, h * 0.85f))
        }
        "Jurassic Park" -> {
            drawCircle(color = Color(0xFFCC8400).copy(alpha = 0.6f), radius = minOf(w, h) * 0.28f, center = Offset(w * 0.5f, h * 0.5f))
            drawCircle(color = Color(0xFF1A1A1A), radius = minOf(w, h) * 0.03f, center = Offset(w * 0.5f, h * 0.5f))
            val fern = Path().apply { moveTo(w * 0.2f, h * 0.9f); lineTo(w * 0.25f, h * 0.4f); lineTo(w * 0.3f, h * 0.9f); close() }
            drawPath(fern, color = RadioactiveGreen.copy(alpha = 0.6f))
        }
        "Home Alone" -> {
            val house = Path().apply { moveTo(w * 0.3f, h * 0.9f); lineTo(w * 0.3f, h * 0.55f); lineTo(w * 0.5f, h * 0.35f); lineTo(w * 0.7f, h * 0.55f); lineTo(w * 0.7f, h * 0.9f); close() }
            drawPath(house, color = colorC)
            drawRect(color = Color(0xFFFFD700), topLeft = Offset(w * 0.42f, h * 0.7f), size = Size(w * 0.16f, h * 0.2f))
            drawCircle(color = ErrorRed, radius = minOf(w, h) * 0.06f, center = Offset(w * 0.82f, h * 0.8f))
        }
        "Finding Nemo" -> {
            val body = Path().apply {
                moveTo(w * 0.25f, h * 0.5f)
                quadraticBezierTo(w * 0.45f, h * 0.3f, w * 0.7f, h * 0.45f)
                quadraticBezierTo(w * 0.8f, h * 0.5f, w * 0.7f, h * 0.55f)
                quadraticBezierTo(w * 0.45f, h * 0.7f, w * 0.25f, h * 0.5f)
                close()
            }
            drawPath(body, color = Color(0xFFFF6A00))
            val tail = Path().apply { moveTo(w * 0.72f, h * 0.4f); lineTo(w * 0.85f, h * 0.35f); lineTo(w * 0.72f, h * 0.6f); close() }
            drawPath(tail, color = Color(0xFFFF6A00))
            drawCircle(color = Color.White, radius = minOf(w, h) * 0.02f, center = Offset(w * 0.32f, h * 0.48f))
        }
        "Beauty and the Beast" -> {
            val castle = Path().apply {
                moveTo(w * 0.2f, h * 0.9f); lineTo(w * 0.2f, h * 0.5f); lineTo(w * 0.3f, h * 0.5f); lineTo(w * 0.3f, h * 0.35f)
                lineTo(w * 0.4f, h * 0.35f); lineTo(w * 0.4f, h * 0.5f); lineTo(w * 0.6f, h * 0.5f); lineTo(w * 0.6f, h * 0.35f)
                lineTo(w * 0.7f, h * 0.35f); lineTo(w * 0.7f, h * 0.5f); lineTo(w * 0.8f, h * 0.5f); lineTo(w * 0.8f, h * 0.9f); close()
            }
            drawPath(castle, color = colorC.copy(alpha = 0.7f))
            drawCircle(color = colorA, radius = minOf(w, h) * 0.1f, center = Offset(w * 0.5f, h * 0.75f))
        }
        "Inception" -> {
            drawCircle(color = colorB.copy(alpha = 0.3f), radius = minOf(w, h) * 0.35f, center = Offset(w * 0.5f, h * 0.45f), style = Stroke(width = minOf(w, h) * 0.015f))
            drawCircle(color = colorB.copy(alpha = 0.3f), radius = minOf(w, h) * 0.22f, center = Offset(w * 0.5f, h * 0.45f), style = Stroke(width = minOf(w, h) * 0.015f))
            drawOval(color = colorA, topLeft = Offset(w * 0.42f, h * 0.4f), size = Size(w * 0.16f, h * 0.08f))
            val top = Path().apply { moveTo(w * 0.44f, h * 0.44f); lineTo(w * 0.56f, h * 0.44f); lineTo(w * 0.5f, h * 0.65f); close() }
            drawPath(top, color = colorA)
        }
        "Forrest Gump" -> {
            drawRect(color = colorC, topLeft = Offset(w * 0.25f, h * 0.6f), size = Size(w * 0.5f, h * 0.06f))
            drawRect(color = colorC, topLeft = Offset(w * 0.3f, h * 0.66f), size = Size(w * 0.04f, h * 0.2f))
            drawRect(color = colorC, topLeft = Offset(w * 0.66f, h * 0.66f), size = Size(w * 0.04f, h * 0.2f))
            val feather = Path().apply {
                moveTo(w * 0.5f, h * 0.15f)
                quadraticBezierTo(w * 0.62f, h * 0.3f, w * 0.5f, h * 0.5f)
                quadraticBezierTo(w * 0.4f, h * 0.3f, w * 0.5f, h * 0.15f)
                close()
            }
            drawPath(feather, color = Color.White.copy(alpha = 0.85f))
        }
        "Spider-Man" -> {
            val cx = w * 0.5f
            val cy = h * 0.5f
            val maxR = minOf(w, h) * 0.45f
            for (i in 0 until 6) {
                val angle = (i / 6f) * 2f * Math.PI.toFloat()
                drawLine(color = Color.White.copy(alpha = 0.5f), start = Offset(cx, cy), end = Offset(cx + kotlin.math.cos(angle) * maxR, cy + kotlin.math.sin(angle) * maxR), strokeWidth = minOf(w, h) * 0.008f)
            }
            for (ring in 1..3) {
                drawCircle(color = Color.White.copy(alpha = 0.4f), radius = maxR * ring / 3.5f, center = Offset(cx, cy), style = Stroke(width = minOf(w, h) * 0.008f))
            }
            drawCircle(color = ErrorRed.copy(alpha = 0.85f), radius = minOf(w, h) * 0.09f, center = Offset(cx, cy))
        }
        "Interstellar" -> {
            drawCircle(color = colorC, radius = minOf(w, h) * 0.18f, center = Offset(w * 0.4f, h * 0.45f))
            drawOval(color = colorA.copy(alpha = 0.6f), topLeft = Offset(w * 0.15f, h * 0.42f), size = Size(w * 0.5f, h * 0.08f))
            drawRect(color = colorB, topLeft = Offset(w * 0.72f, h * 0.6f), size = Size(w * 0.08f, h * 0.22f))
        }
        "Fight Club" -> {
            drawRect(color = Color(0xFFF0E6D2), topLeft = Offset(w * 0.35f, h * 0.4f), size = Size(w * 0.3f, h * 0.2f))
            drawLine(color = colorA, start = Offset(w * 0.3f, h * 0.3f), end = Offset(w * 0.7f, h * 0.7f), strokeWidth = minOf(w, h) * 0.025f)
            drawLine(color = colorA, start = Offset(w * 0.7f, h * 0.3f), end = Offset(w * 0.3f, h * 0.7f), strokeWidth = minOf(w, h) * 0.025f)
        }
        "The Lion King" -> {
            drawCircle(color = Color(0xFFFF6A00), radius = minOf(w, h) * 0.22f, center = Offset(w * 0.5f, h * 0.35f))
            val rock = Path().apply { moveTo(w * 0.15f, h * 0.9f); lineTo(w * 0.35f, h * 0.55f); lineTo(w * 0.55f, h * 0.7f); lineTo(w * 0.85f, h * 0.9f); close() }
            drawPath(rock, color = Color(0xFF5A3A1A))
        }
        "Die Hard" -> {
            drawRect(color = colorC, topLeft = Offset(w * 0.35f, h * 0.2f), size = Size(w * 0.3f, h * 0.7f))
            drawCircle(color = Color(0xFFFF6A00).copy(alpha = 0.8f), radius = minOf(w, h) * 0.12f, center = Offset(w * 0.7f, h * 0.35f))
        }
        "Back to the Future" -> {
            val bolt = Path().apply {
                moveTo(w * 0.55f, h * 0.1f); lineTo(w * 0.4f, h * 0.45f); lineTo(w * 0.52f, h * 0.45f)
                lineTo(w * 0.4f, h * 0.85f); lineTo(w * 0.68f, h * 0.4f); lineTo(w * 0.55f, h * 0.4f); close()
            }
            drawPath(bolt, color = Color(0xFFFFD700))
        }
        "The Godfather" -> {
            drawCircle(color = Color(0xFFFF6A00), radius = minOf(w, h) * 0.22f, center = Offset(w * 0.5f, h * 0.55f))
            val leaf = Path().apply { moveTo(w * 0.5f, h * 0.32f); lineTo(w * 0.58f, h * 0.2f); lineTo(w * 0.5f, h * 0.24f); close() }
            drawPath(leaf, color = RadioactiveGreen)
        }
        "Toy Story" -> {
            drawStar(Offset(w * 0.35f, h * 0.4f), minOf(w, h) * 0.16f, Color(0xFFFFD700))
            val hatBrim = Path().apply { moveTo(w * 0.55f, h * 0.65f); lineTo(w * 0.9f, h * 0.65f); lineTo(w * 0.8f, h * 0.72f); lineTo(w * 0.6f, h * 0.72f); close() }
            drawPath(hatBrim, color = Color(0xFF8B5A2B))
            drawCircle(color = Color(0xFF8B5A2B), radius = minOf(w, h) * 0.12f, center = Offset(w * 0.72f, h * 0.55f))
        }
        "E.T. the Extra-Terrestrial" -> {
            drawCircle(color = Color(0xFFFFD700).copy(alpha = 0.7f), radius = minOf(w, h) * 0.28f, center = Offset(w * 0.6f, h * 0.35f))
            drawCircle(color = colorC, radius = minOf(w, h) * 0.1f, center = Offset(w * 0.3f, h * 0.75f), style = Stroke(width = minOf(w, h) * 0.02f))
            drawCircle(color = colorC, radius = minOf(w, h) * 0.1f, center = Offset(w * 0.55f, h * 0.75f), style = Stroke(width = minOf(w, h) * 0.02f))
            drawLine(color = colorC, start = Offset(w * 0.3f, h * 0.75f), end = Offset(w * 0.42f, h * 0.6f), strokeWidth = minOf(w, h) * 0.015f)
            drawLine(color = colorC, start = Offset(w * 0.42f, h * 0.6f), end = Offset(w * 0.55f, h * 0.75f), strokeWidth = minOf(w, h) * 0.015f)
        }
        "Frozen" -> {
            val cx = w * 0.5f
            val cy = h * 0.5f
            val r = minOf(w, h) * 0.35f
            for (i in 0 until 6) {
                val angle = (i / 6f) * Math.PI.toFloat()
                val x1 = cx - kotlin.math.cos(angle) * r
                val y1 = cy - kotlin.math.sin(angle) * r
                val x2 = cx + kotlin.math.cos(angle) * r
                val y2 = cy + kotlin.math.sin(angle) * r
                drawLine(color = Color.White.copy(alpha = 0.85f), start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = minOf(w, h) * 0.015f)
            }
            drawCircle(color = colorA.copy(alpha = 0.3f), radius = r * 1.2f, center = Offset(cx, cy), style = Stroke(width = minOf(w, h) * 0.01f))
        }
        "Avatar" -> {
            val wing = Path().apply {
                moveTo(w * 0.2f, h * 0.6f)
                quadraticBezierTo(w * 0.5f, h * 0.2f, w * 0.85f, h * 0.5f)
                quadraticBezierTo(w * 0.5f, h * 0.5f, w * 0.2f, h * 0.6f)
                close()
            }
            drawPath(wing, color = colorC.copy(alpha = 0.6f))
            val dots = listOf(0.3f to 0.7f, 0.5f to 0.8f, 0.65f to 0.65f, 0.4f to 0.5f)
            for ((fx, fy) in dots) drawCircle(color = NeonCyan.copy(alpha = 0.9f), radius = minOf(w, h) * 0.02f, center = Offset(w * fx, h * fy))
        }
        "Ghostbusters" -> {
            val ghost = Path().apply {
                moveTo(w * 0.35f, h * 0.7f)
                lineTo(w * 0.35f, h * 0.4f)
                quadraticBezierTo(w * 0.5f, h * 0.2f, w * 0.65f, h * 0.4f)
                lineTo(w * 0.65f, h * 0.7f)
                lineTo(w * 0.58f, h * 0.62f)
                lineTo(w * 0.5f, h * 0.7f)
                lineTo(w * 0.42f, h * 0.62f)
                close()
            }
            drawPath(ghost, color = Color.White.copy(alpha = 0.9f))
            drawCircle(color = ErrorRed, radius = minOf(w, h) * 0.32f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(width = minOf(w, h) * 0.02f))
            drawLine(color = ErrorRed, start = Offset(w * 0.28f, h * 0.72f), end = Offset(w * 0.72f, h * 0.28f), strokeWidth = minOf(w, h) * 0.02f)
        }
        "The Sixth Sense" -> {
            drawRect(color = colorC, topLeft = Offset(w * 0.35f, h * 0.25f), size = Size(w * 0.3f, h * 0.6f))
            drawCircle(color = ErrorRed, radius = minOf(w, h) * 0.025f, center = Offset(w * 0.6f, h * 0.55f))
        }
        "Alien" -> {
            drawOval(color = colorC, topLeft = Offset(w * 0.35f, h * 0.3f), size = Size(w * 0.3f, h * 0.45f))
            drawCircle(color = RadioactiveGreen.copy(alpha = 0.6f), radius = minOf(w, h) * 0.06f, center = Offset(w * 0.5f, h * 0.5f))
        }
        "Rocky" -> {
            for (i in 0 until 4) {
                drawRect(color = colorC, topLeft = Offset(w * (0.2f + i * 0.12f), h * (0.9f - i * 0.12f)), size = Size(w * 0.12f, h * 0.12f))
            }
            drawCircle(color = ErrorRed, radius = minOf(w, h) * 0.14f, center = Offset(w * 0.75f, h * 0.3f))
        }
        "The Terminator" -> {
            drawCircle(color = ErrorRed, radius = minOf(w, h) * 0.1f, center = Offset(w * 0.5f, h * 0.45f))
            drawCircle(color = ErrorRed.copy(alpha = 0.3f), radius = minOf(w, h) * 0.18f, center = Offset(w * 0.5f, h * 0.45f))
            drawLine(color = colorC, start = Offset(w * 0.25f, h * 0.7f), end = Offset(w * 0.75f, h * 0.7f), strokeWidth = minOf(w, h) * 0.03f)
        }
        "Grease" -> {
            drawRect(color = Color(0xFFFF0055), topLeft = Offset(w * 0.25f, h * 0.55f), size = Size(w * 0.5f, h * 0.15f))
            drawCircle(color = Color(0xFF1A1A1A), radius = minOf(w, h) * 0.06f, center = Offset(w * 0.35f, h * 0.72f))
            drawCircle(color = Color(0xFF1A1A1A), radius = minOf(w, h) * 0.06f, center = Offset(w * 0.65f, h * 0.72f))
            drawCircle(color = Color.White, radius = minOf(w, h) * 0.03f, center = Offset(w * 0.5f, h * 0.35f))
            drawRect(color = Color.White, topLeft = Offset(w * 0.52f, h * 0.2f), size = Size(w * 0.02f, h * 0.15f))
        }
        "Mean Girls" -> {
            drawRect(color = Color(0xFFFF3D8A), topLeft = Offset(w * 0.3f, h * 0.5f), size = Size(w * 0.4f, h * 0.3f))
            val flame = Path().apply { moveTo(w * 0.45f, h * 0.5f); quadraticBezierTo(w * 0.5f, h * 0.3f, w * 0.55f, h * 0.5f); close() }
            drawPath(flame, color = Color(0xFFFF6A00))
        }
        "King Kong" -> {
            drawRect(color = colorC, topLeft = Offset(w * 0.44f, h * 0.15f), size = Size(w * 0.12f, h * 0.75f))
            drawCircle(color = Color(0xFF3A2A1A), radius = minOf(w, h) * 0.12f, center = Offset(w * 0.5f, h * 0.3f))
        }
        else -> return false
    }
    return true
}

private fun DrawScope.drawStar(center: Offset, radius: Float, color: Color) {
    val path = Path()
    val points = 5
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) radius else radius * 0.4f
        val angle = (i * Math.PI / points) - Math.PI / 2
        val x = center.x + (r * kotlin.math.cos(angle)).toFloat()
        val y = center.y + (r * kotlin.math.sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = color)
}

private fun DrawScope.drawMoodPoster(mood: Mood, colorA: Color, colorB: Color, colorC: Color, w: Float, h: Float) {
    when (mood) {
        Mood.HORROR -> {
            drawCircle(color = colorA.copy(alpha = 0.15f), radius = minOf(w, h) * 0.6f, center = Offset(w * 0.5f, h * 0.5f))
            val crack = Path().apply {
                moveTo(w * 0.5f, 0f)
                lineTo(w * 0.38f, h * 0.28f)
                lineTo(w * 0.58f, h * 0.42f)
                lineTo(w * 0.32f, h * 0.68f)
                lineTo(w * 0.5f, h)
            }
            drawPath(crack, color = colorA, style = Stroke(width = minOf(w, h) * 0.025f))
            val branch = Path().apply {
                moveTo(w * 0.58f, h * 0.42f)
                lineTo(w * 0.74f, h * 0.52f)
            }
            drawPath(branch, color = colorA, style = Stroke(width = minOf(w, h) * 0.018f))
        }
        Mood.CRIME -> {
            val beam1 = Path().apply { moveTo(0f, 0f); lineTo(w * 0.45f, h); lineTo(w * 0.15f, h); close() }
            val beam2 = Path().apply { moveTo(w, 0f); lineTo(w * 0.85f, h); lineTo(w * 0.55f, h); close() }
            drawPath(beam1, color = colorA.copy(alpha = 0.18f))
            drawPath(beam2, color = colorA.copy(alpha = 0.18f))
            drawCircle(color = colorA.copy(alpha = 0.9f), radius = minOf(w, h) * 0.09f, center = Offset(w * 0.5f, h * 0.42f))
        }
        Mood.SCIFI -> {
            val cx = w * 0.5f
            val cy = h * 0.55f
            val maxR = minOf(w, h * 2f) * 0.4f
            val ringColors = listOf(colorA, colorB, colorC)
            for (i in 0 until 3) {
                drawCircle(
                    color = ringColors[i % 3].copy(alpha = 0.55f),
                    radius = maxR * (1f - i * 0.28f),
                    center = Offset(cx, cy),
                    style = Stroke(width = minOf(w, h) * 0.02f)
                )
            }
            val starSpots = listOf(0.1f to 0.15f, 0.85f to 0.2f, 0.15f to 0.8f, 0.9f to 0.75f, 0.5f to 0.08f)
            for ((fx, fy) in starSpots) {
                drawCircle(color = Color.White.copy(alpha = 0.85f), radius = minOf(w, h) * 0.014f, center = Offset(w * fx, h * fy))
            }
        }
        Mood.ACTION -> {
            val cx = w * 0.5f
            val cy = h * 0.5f
            drawCircle(color = colorA.copy(alpha = 0.9f), radius = minOf(w, h) * 0.16f, center = Offset(cx, cy))
            val rayCount = 8
            val innerR = minOf(w, h) * 0.2f
            val outerR = minOf(w, h) * 0.58f
            val spread = 0.18f
            for (i in 0 until rayCount) {
                val angle = (i.toFloat() / rayCount) * 2f * Math.PI.toFloat()
                val x1 = cx + kotlin.math.cos(angle - spread) * innerR
                val y1 = cy + kotlin.math.sin(angle - spread) * innerR
                val x2 = cx + kotlin.math.cos(angle + spread) * innerR
                val y2 = cy + kotlin.math.sin(angle + spread) * innerR
                val x3 = cx + kotlin.math.cos(angle) * outerR
                val y3 = cy + kotlin.math.sin(angle) * outerR
                val path = Path().apply { moveTo(x1, y1); lineTo(x3, y3); lineTo(x2, y2); close() }
                drawPath(path, color = (if (i % 2 == 0) colorB else colorA).copy(alpha = 0.7f))
            }
        }
        Mood.FANTASY -> {
            val m1 = Path().apply { moveTo(0f, h); lineTo(w * 0.3f, h * 0.35f); lineTo(w * 0.6f, h); close() }
            val m2 = Path().apply { moveTo(w * 0.35f, h); lineTo(w * 0.7f, h * 0.5f); lineTo(w, h); close() }
            drawPath(m1, color = colorA.copy(alpha = 0.5f))
            drawPath(m2, color = colorC.copy(alpha = 0.7f))
            drawCircle(color = colorB.copy(alpha = 0.9f), radius = minOf(w, h) * 0.1f, center = Offset(w * 0.5f, h * 0.22f))
        }
        Mood.FAMILY -> {
            val colors = listOf(colorA, colorB, colorC)
            val positions = listOf(0.3f to 0.4f, 0.62f to 0.55f, 0.45f to 0.72f, 0.78f to 0.3f)
            positions.forEachIndexed { i, (fx, fy) ->
                drawCircle(color = colors[i % 3].copy(alpha = 0.7f), radius = minOf(w, h) * 0.22f, center = Offset(w * fx, h * fy))
            }
        }
        Mood.ROMANCE -> {
            val cx = w * 0.5f
            val cy = h * 0.5f
            val s = minOf(w, h) * 0.5f
            drawCircle(color = colorA.copy(alpha = 0.85f), radius = s * 0.28f, center = Offset(cx - s * 0.26f, cy - s * 0.12f))
            drawCircle(color = colorA.copy(alpha = 0.85f), radius = s * 0.28f, center = Offset(cx + s * 0.26f, cy - s * 0.12f))
            val heartBottom = Path().apply {
                moveTo(cx - s * 0.5f, cy - s * 0.05f)
                lineTo(cx, cy + s * 0.55f)
                lineTo(cx + s * 0.5f, cy - s * 0.05f)
                close()
            }
            drawPath(heartBottom, color = colorA.copy(alpha = 0.85f))
        }
    }
}

private fun DrawScope.drawGenericPoster(template: Int, colorA: Color, colorB: Color, colorC: Color, w: Float, h: Float, pool: FloatCursor) {
    when (template) {
        0 -> { // Concentric rings
            val cx = w * (0.35f + pool.next() * 0.3f)
            val cy = h * 0.5f
            val maxR = minOf(w, h * 2f) * 0.42f
            val colors = listOf(colorA, colorB, colorC)
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
                val color = if (i % 2 == 0) colorA else colorB
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
                val cx = w * pool.next()
                val cy = h * pool.next()
                val r = minOf(w, h) * (0.06f + 0.16f * pool.next())
                val color = listOf(colorA, colorB, colorC)[i % 3]
                drawCircle(color = color.copy(alpha = 0.55f), radius = r, center = Offset(cx, cy))
            }
        }
        3 -> { // Triangle cluster
            val triangles = listOf(colorA, colorB, colorC)
            for (i in 0 until 3) {
                val baseX = w * (0.15f + i * 0.3f + pool.next() * 0.1f)
                val baseY = h * (0.85f + pool.next() * 0.1f)
                val height = h * (0.5f + pool.next() * 0.3f)
                val width = w * (0.25f + pool.next() * 0.15f)
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
            drawCircle(color = colorA.copy(alpha = 0.9f), radius = minOf(w, h) * 0.14f, center = Offset(cx, cy))
            val rayCount = 10
            val innerR = minOf(w, h) * 0.18f
            val outerR = minOf(w, h) * 0.62f
            val spread = 0.12f
            for (i in 0 until rayCount) {
                val angle = (i.toFloat() / rayCount) * 2f * Math.PI.toFloat() + pool.next()
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
                val color = if (i % 2 == 0) colorB else colorC
                drawPath(path, color = color.copy(alpha = 0.5f))
            }
        }
    }
}
