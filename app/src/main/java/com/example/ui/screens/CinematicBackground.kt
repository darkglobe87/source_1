package com.example.ui.screens

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import kotlinx.coroutines.isActive

/**
 * Full-screen animated cinema backdrop: drifting fog, sweeping projector light
 * rays, film grain and a vignette that reacts to game state. Runs as a GPU AGSL
 * shader on API 33+; falls back to the plain particle dust plus a cheap overlay
 * on older devices, since RuntimeShader needs Tiramisu.
 *
 * @param dangerLevel 0..1, shifts the palette toward red as lives run low.
 * @param pulse 0..1 transient flash (expected to decay back to 0), for correct/win/lose beats.
 * @param pulseColor tint of the flash - success green, error red, hint cyan, etc.
 */
@Composable
fun LivingBackground(
    modifier: Modifier = Modifier,
    dangerLevel: Float = 0f,
    pulse: Float = 0f,
    pulseColor: Color = NeonCyan
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ShaderCinematicBackground(modifier, dangerLevel, pulse, pulseColor)
    } else {
        LegacyReactiveBackground(modifier, dangerLevel, pulse, pulseColor)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderCinematicBackground(
    modifier: Modifier,
    dangerLevel: Float,
    pulse: Float,
    pulseColor: Color
) {
    val shader = remember { RuntimeShader(CINEMATIC_AGSL) }
    var timeSec by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now -> timeSec = (now - start) / 1_000_000_000f }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // Reading timeSec here only invalidates the draw phase, not full recomposition.
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("iTime", timeSec)
        shader.setFloatUniform("colorA", NeonPurple.red, NeonPurple.green, NeonPurple.blue)
        shader.setFloatUniform("colorB", NeonCyan.red, NeonCyan.green, NeonCyan.blue)
        shader.setFloatUniform("dangerAmt", dangerLevel.coerceIn(0f, 1f))
        shader.setFloatUniform("pulseAmt", pulse.coerceIn(0f, 1f))
        shader.setFloatUniform("pulseColor", pulseColor.red, pulseColor.green, pulseColor.blue)
        drawRect(brush = ShaderBrush(shader))
    }
}

/**
 * Cheap non-shader stand-in for pre-Tiramisu devices: the existing particle
 * dust plus a vignette and color-pulse overlay, so the reactive feel isn't
 * lost even where AGSL isn't available.
 */
@Composable
private fun LegacyReactiveBackground(
    modifier: Modifier,
    dangerLevel: Float,
    pulse: Float,
    pulseColor: Color
) {
    Box(modifier = modifier.fillMaxSize()) {
        ParticleBackground()
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = kotlin.math.max(size.width, size.height) * 0.75f

            val danger = dangerLevel.coerceIn(0f, 1f)
            if (danger > 0f) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(ErrorRed.copy(alpha = 0f), ErrorRed.copy(alpha = danger * 0.22f)),
                        center = center,
                        radius = radius
                    )
                )
            }

            val p = pulse.coerceIn(0f, 1f)
            if (p > 0f) {
                drawRect(color = pulseColor.copy(alpha = p * 0.18f))
            }

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, DarkBackground.copy(alpha = 0.55f)),
                    center = center,
                    radius = radius
                )
            )
        }
    }
}

private const val CINEMATIC_AGSL = """
uniform float2 resolution;
uniform float iTime;
uniform float3 colorA;
uniform float3 colorB;
uniform float dangerAmt;
uniform float pulseAmt;
uniform float3 pulseColor;

float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float a = hash(i);
    float b = hash(i + float2(1.0, 0.0));
    float c = hash(i + float2(0.0, 1.0));
    float d = hash(i + float2(1.0, 1.0));
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float fbm(float2 p) {
    float value = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        value += amp * noise(p);
        p *= 2.02;
        amp *= 0.5;
    }
    return value;
}

// One layer of tiny drifting motes: a grid of cells, each with a randomly
// placed, randomly sized dot that twinkles and slowly scrolls. Stacking a
// few of these at different densities/speeds gives a parallax "snow" feel.
float snowLayer(float2 p, float density, float speed, float seed, float time) {
    float2 grid = p * density;
    grid.y += time * speed;
    grid.x += sin(time * 0.25 + seed) * 0.4;
    float2 cell = floor(grid);
    float2 f = fract(grid) - 0.5;
    float rnd = hash(cell + seed);
    float2 jitter = float2(hash(cell + seed + 3.1), hash(cell + seed + 7.7)) - 0.5;
    float d = length(f - jitter * 0.7);
    float twinkle = 0.5 + 0.5 * sin(time * 3.5 + rnd * 30.0);
    float dotSize = mix(0.035, 0.11, rnd);
    return smoothstep(dotSize, 0.0, d) * twinkle;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / resolution;
    float aspect = resolution.x / resolution.y;
    float2 centered = (uv - 0.5) * float2(aspect, 1.0);

    // Drifting volumetric fog - two overlapping FBM layers moving at different speeds/scales
    float2 fogUv = uv * float2(aspect, 1.0) * 1.6 + float2(iTime * 0.015, -iTime * 0.008);
    float fog = fbm(fogUv);
    float fog2 = fbm(fogUv * 1.7 + float2(5.2, 1.3) - iTime * 0.01);
    float fogMix = mix(fog, fog2, 0.5);

    // Projector light rays sweeping slowly from a point above the frame
    float angle = atan(centered.x, -centered.y + 0.65);
    float rays = sin(angle * 6.0 + iTime * 0.15) * 0.5 + 0.5;
    rays = pow(rays, 3.0);
    float raysFalloff = smoothstep(1.1, 0.1, length(centered));
    rays *= raysFalloff;

    float3 base = mix(colorA, colorB, fogMix);
    float3 dangerTint = float3(1.0, 0.15, 0.08);
    base = mix(base, dangerTint, dangerAmt * 0.6);

    float3 col = base * (0.10 + fogMix * 0.18);
    col += rays * float3(1.0, 1.0, 1.0) * 0.10;
    col += pulseColor * pulseAmt * 0.5;

    // Radioactive snow - three depth layers of tiny floating motes
    float2 snowUv = uv * float2(aspect, 1.0);
    float snowNear = snowLayer(snowUv, 9.0, 0.045, 1.0, iTime);
    float snowMid = snowLayer(snowUv, 15.0, 0.075, 41.0, iTime);
    float snowFar = snowLayer(snowUv, 23.0, 0.11, 97.0, iTime);
    float3 snowColor = float3(0.68, 0.92, 0.0);
    col += snowColor * (snowNear * 0.55 + snowMid * 0.4 + snowFar * 0.28);

    // Vignette
    float vig = smoothstep(0.95, 0.25, length(centered));
    col *= mix(0.35, 1.0, vig);

    // Film grain
    float grain = hash(fragCoord + iTime * 60.0) - 0.5;
    col += grain * 0.035;

    // Faint scanlines
    float scan = sin(fragCoord.y * 1.5) * 0.015;
    col -= scan;

    col = max(col, float3(0.0, 0.0, 0.0));
    return half4(col, 1.0);
}
"""
