package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeonCyan

/**
 * A single letter tile. Size is dynamic (passed by the caller based on the longest
 * unbreakable word/segment on screen) so long or hyphenated titles shrink to fit
 * instead of overflowing. Reveals with a 3D flip rather than an instant swap.
 */
@Composable
fun LetterBox(
    char: Char,
    isRevealed: Boolean,
    modifier: Modifier = Modifier,
    isSpace: Boolean = false,
    size: Dp = 40.dp
) {
    if (isSpace) {
        Box(modifier = modifier.size(size))
        return
    }

    val density = LocalDensity.current.density
    val cornerRadius = (size.value * 0.2f).dp
    val fontSize = (size.value * 0.6f).sp

    // 0 -> hidden face, 180 -> revealed face. Doesn't animate on first composition,
    // so already-revealed punctuation just appears revealed with no flip.
    val rotation by animateFloatAsState(
        targetValue = if (isRevealed) 180f else 0f,
        animationSpec = tween(durationMillis = 380),
        label = "letterFlip"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            },
        contentAlignment = Alignment.Center
    ) {
        if (rotation <= 90f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color.Transparent)
                    .border(2.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(cornerRadius))
            )
        } else {
            // Past the halfway point of the flip, the box is showing its "back" —
            // counter-rotate the content so the letter reads normally, not mirrored.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(2.dp, NeonCyan, RoundedCornerShape(cornerRadius)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = char.uppercase(),
                    color = NeonCyan,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
