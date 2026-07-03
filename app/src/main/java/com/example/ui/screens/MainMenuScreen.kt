package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppRepository
import com.example.audio.MusicManager
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple

@Composable
fun MainMenuScreen(
    repository: AppRepository,
    onPlay: () -> Unit,
    onNavigateToStore: () -> Unit
) {
    val userState by repository.userState.collectAsState(initial = null)
    val isMuted by MusicManager.isMuted.collectAsState()
    var showHowToPlay by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val visibleState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { visibleState.value = true }

    if (showHowToPlay) {
        HowToPlayDialog(onDismiss = { showHowToPlay = false })
    }

    // Slow ambient pulse for the glow behind the title - subtle, not distracting
    val infiniteTransition = rememberInfiniteTransition(label = "menuGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    // Gentle 3D drift on the title, like it's floating - kept small on purpose
    val titleTiltX by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "titleTiltX"
    )
    val titleTiltY by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "titleTiltY"
    )
    val density = androidx.compose.ui.platform.LocalDensity.current.density

    Box(modifier = Modifier.fillMaxSize()) {
        LivingBackground()

        IconButton(
            onClick = { MusicManager.toggleMute() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(20.dp)
        ) {
            Icon(
                if (isMuted) Icons.Default.MusicOff else Icons.Default.MusicNote,
                contentDescription = if (isMuted) "Unmute music" else "Mute music",
                tint = NeonCyan
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Star, contentDescription = "Coins", tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("${userState?.coins ?: 0}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = visibleState.value,
                enter = scaleIn(tween(600, easing = FastOutSlowInEasing)) + fadeIn(tween(600))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(300.dp, 110.dp)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(NeonPurple.copy(alpha = glowAlpha), Color.Transparent)
                                    )
                                )
                        )
                        Text(
                            text = "BAD PLOTS",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp,
                            color = NeonCyan,
                            modifier = Modifier.graphicsLayer {
                                rotationX = titleTiltX
                                rotationY = titleTiltY
                                cameraDistance = 30f * density
                            }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Guess the movie from the worst description ever written.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }

            Spacer(Modifier.height(56.dp))

            AnimatedVisibility(
                visible = visibleState.value,
                enter = fadeIn(tween(600, delayMillis = 200)) + scaleIn(tween(600, delayMillis = 200), initialScale = 0.9f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = onPlay,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                        modifier = Modifier.width(220.dp).height(56.dp)
                    ) {
                        Text("PLAY", fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 2.sp)
                    }

                    Spacer(Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = onNavigateToStore,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPink),
                        modifier = Modifier.width(220.dp).height(48.dp)
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("STORE", fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(12.dp))

                    TextButton(onClick = { showHowToPlay = true }) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("How to Play", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
