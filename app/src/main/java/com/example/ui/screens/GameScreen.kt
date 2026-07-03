package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
import com.example.audio.MusicManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.KeyboardKey
import com.example.ui.render3d.LetterTile3DGrid
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.RadioactiveGreen
import com.example.ui.theme.RadioactiveGreenDim
import com.example.ui.theme.RadioactiveYellowGreen
import com.example.ui.theme.CorrectGreen
import com.example.ui.theme.ErrorRed

import com.example.ui.theme.NeonPurple
import com.example.viewmodel.GameStatus
import com.example.viewmodel.GameViewModel

data class Particle(
    var x: Float, // normalized 0f..1f across the screen
    var y: Float, // normalized 0f..1f
    var vx: Float,
    var vy: Float,
    val radiusDp: Float,
    val phase: Float,
    val color: Color
)

@Composable
fun ParticleBackground(particleCount: Int = 70) {
    val particles = androidx.compose.runtime.remember {
        List(particleCount) {
            Particle(
                x = kotlin.random.Random.nextFloat(),
                y = kotlin.random.Random.nextFloat(),
                vx = (kotlin.random.Random.nextFloat() - 0.5f) * 0.00035f,
                vy = (kotlin.random.Random.nextFloat() - 0.5f) * 0.00035f,
                radiusDp = kotlin.random.Random.nextFloat() * 2.5f + 1.5f,
                phase = kotlin.random.Random.nextFloat() * 6.283f,
                color = listOf(RadioactiveGreen, RadioactiveGreenDim, RadioactiveYellowGreen).random()
            )
        }
    }

    var time by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        var lastTime = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { frameTime ->
                val dt = (frameTime - lastTime) / 16_000_000f // normalized dt
                lastTime = frameTime
                time = frameTime

                // Drift slowly and bounce off the edges, like dust settling in still air
                for (p in particles) {
                    p.x += p.vx * dt
                    p.y += p.vy * dt

                    if (p.x < 0f || p.x > 1f) {
                        p.vx = -p.vx
                        p.x = p.x.coerceIn(0f, 1f)
                    }
                    if (p.y < 0f || p.y > 1f) {
                        p.vy = -p.vy
                        p.y = p.y.coerceIn(0f, 1f)
                    }
                }
            }
        }
    }

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        // Reading time here only invalidates the draw phase, not full recomposition
        time.let {}

        val width = size.width
        val height = size.height
        val seconds = time / 1_000_000_000f

        for (p in particles) {
            val cx = p.x * width
            val cy = p.y * height
            val radiusPx = p.radiusDp.dp.toPx()
            // Gentle flicker so the dust looks like it's glowing, not static
            val pulse = 0.6f + 0.4f * kotlin.math.sin(seconds * 1.1f + p.phase)
            val alpha = (0.22f * pulse).coerceIn(0.04f, 0.3f)
            val glowRadius = radiusPx * 2.2f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(p.color.copy(alpha = alpha), p.color.copy(alpha = 0f)),
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = androidx.compose.ui.geometry.Offset(cx, cy)
            )
        }
    }
}

@Composable
fun HowToPlayDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How to Play", color = NeonCyan) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Read the hilariously bad description of a famous movie.")
                Text("2. Guess the letters to reveal the movie's title.")
                Text("3. Use your coins to buy hints if you get stuck.")
                Text("4. Guess correctly to earn more coins and progress to new movies!")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it", color = NeonPink)
            }
        },
        containerColor = DarkSurface
    )
}

/**
 * Splits a word into pieces that can each independently wrap onto a new line -
 * on spaces (handled by the caller) and after hyphens, so "Spider-Man" can break
 * into "Spider-" / "Man" instead of being one unbreakable 10-letter run.
 */
internal fun splitIntoWrapSegments(word: String): List<String> {
    val segments = mutableListOf<String>()
    val current = StringBuilder()
    for (c in word) {
        current.append(c)
        if (c == '-') {
            segments.add(current.toString())
            current.clear()
        }
    }
    if (current.isNotEmpty()) segments.add(current.toString())
    return segments
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onNavigateToStore: () -> Unit,
    onNavigateHome: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val userState by viewModel.userState.collectAsState()
    val isMuted by MusicManager.isMuted.collectAsState()
    var showHowToPlay by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val snackbarHostState = androidx.compose.runtime.remember { SnackbarHostState() }

    if (showHowToPlay) {
        HowToPlayDialog(onDismiss = { showHowToPlay = false })
    }

    androidx.compose.runtime.LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeSnackbarMessage()
        }
    }

    val density = LocalDensity.current.density
    val levelTransition = androidx.compose.runtime.remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(uiState.currentMovie?.id) {
        levelTransition.snapTo(0f)
        levelTransition.animateTo(1f, animationSpec = tween(550, easing = FastOutSlowInEasing))
    }

    // Background reacts to how close the player is to losing - calm until lives
    // run low, then ramps quickly rather than linearly, so it stays subtle early on.
    val dangerTarget = ((8 - uiState.lives).coerceIn(0, 8) / 8f).let { it * it }
    val dangerLevel by animateFloatAsState(targetValue = dangerTarget, animationSpec = tween(500), label = "danger")

    // A brief background flash on win/loss - snaps to full and decays back to 0.
    val pulseAnim = androidx.compose.runtime.remember { Animatable(0f) }
    var pulseColor by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(NeonCyan) }
    androidx.compose.runtime.LaunchedEffect(uiState.status) {
        when (uiState.status) {
            GameStatus.Won -> {
                pulseColor = CorrectGreen
                pulseAnim.snapTo(1f)
                pulseAnim.animateTo(0f, animationSpec = tween(900, easing = FastOutSlowInEasing))
            }
            GameStatus.Lost -> {
                pulseColor = ErrorRed
                pulseAnim.snapTo(1f)
                pulseAnim.animateTo(0f, animationSpec = tween(900, easing = FastOutSlowInEasing))
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("BAD PLOTS", fontWeight = FontWeight.Black, color = NeonCyan, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavigateHome) {
                        Icon(Icons.Default.Home, contentDescription = "Main Menu", tint = NeonCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    IconButton(onClick = { MusicManager.toggleMute() }) {
                        Icon(
                            if (isMuted) Icons.Default.MusicOff else Icons.Default.MusicNote,
                            contentDescription = if (isMuted) "Unmute music" else "Mute music",
                            tint = NeonCyan
                        )
                    }
                    IconButton(onClick = { showHowToPlay = true }) {
                        Icon(Icons.Default.Info, contentDescription = "How to Play", tint = NeonCyan)
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = "Coins", tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("${userState?.coins ?: 0}", fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = onNavigateToStore) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = "Store", tint = NeonPink)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LivingBackground(dangerLevel = dangerLevel, pulse = pulseAnim.value, pulseColor = pulseColor)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            if (uiState.isLoadingNewLevel) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = NeonCyan)
                        Spacer(Modifier.height(16.dp))
                        Text("Loading next movie...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
                return@Scaffold
            }
            
            if (uiState.error != null) {
                Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.retryLevel() }, colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)) {
                    Text("Retry")
                }
                return@Scaffold
            }

            // Game Status Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Level ${ (userState?.currentLevelIndex ?: 0) + 1 }", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Lives: ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${uiState.lives}", color = if (uiState.lives > 3) NeonCyan else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bad Description Card + Word Layout - swoops in with a 3D rotation each
            // time the level changes, and sizes letters to whatever the longest
            // unbreakable segment needs so nothing overflows the screen.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val progress = levelTransition.value
                        rotationY = (1f - progress) * 50f
                        alpha = progress.coerceIn(0f, 1f)
                        cameraDistance = 24f * density
                    }
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        val imageName = uiState.currentMovie?.imageRes
                        if (imageName != null) {
                            val context = LocalContext.current
                            val resId = androidx.compose.runtime.remember(imageName) {
                                context.resources.getIdentifier(imageName, "drawable", context.packageName)
                            }
                            if (resId != 0) {
                                Image(
                                    painter = painterResource(id = resId),
                                    contentDescription = "Movie art",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                        Text(
                            text = "\"${uiState.currentMovie?.badDescription}\"",
                            fontSize = 18.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 24.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Word Layout - real 3D tiles (see com.example.ui.render3d), not the
                // old FlowRow-of-2D-boxes.
                LetterTile3DGrid(
                    title = uiState.currentMovie?.title ?: "",
                    guessedLetters = uiState.guessedLetters,
                    dangerLevel = dangerLevel,
                    accentColor = NeonCyan
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // End Game State
            if (uiState.status != GameStatus.Playing) {
                val won = uiState.status == GameStatus.Won
                val titleColor = if (won) NeonCyan else MaterialTheme.colorScheme.error
                
                val bannerProgress = androidx.compose.runtime.remember(uiState.status) { Animatable(0f) }
                androidx.compose.runtime.LaunchedEffect(uiState.status) {
                    bannerProgress.animateTo(1f, animationSpec = tween(550, easing = FastOutSlowInEasing))
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer {
                        val p = bannerProgress.value
                        rotationX = (1f - p) * -40f
                        alpha = p.coerceIn(0f, 1f)
                        scaleX = 0.85f + 0.15f * p
                        scaleY = 0.85f + 0.15f * p
                        cameraDistance = 24f * density
                    }
                ) {
                    Text(
                        text = if (won) "CORRECT!" else "GAME OVER",
                        color = titleColor,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black
                    )
                    if (!won) {
                        Text("It was: ${uiState.currentMovie?.title}", color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.nextLevel() },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("NEXT LEVEL")
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // Hint System
                val costLetter = if (userState?.hasPurchasedAdFree == true) 0 else 10
                val costClue = if (userState?.hasPurchasedAdFree == true) 0 else 15
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    HintButton(
                        icon = Icons.Default.Lightbulb,
                        label = "Letter",
                        cost = costLetter,
                        isRevealed = false,
                        onClick = { viewModel.useLetterHint() }
                    )
                    HintButton(
                        icon = Icons.Default.Person,
                        label = "Character",
                        cost = costClue,
                        isRevealed = uiState.revealedCharacterHint,
                        onClick = { viewModel.revealClue("character") }
                    )
                    HintButton(
                        icon = Icons.Default.Movie,
                        label = "Scene",
                        cost = costClue,
                        isRevealed = uiState.revealedSceneHint,
                        onClick = { viewModel.revealClue("scene") }
                    )
                }

                // Show active clues
                Column(modifier = Modifier.padding(top = 16.dp).fillMaxWidth()) {
                    if (uiState.revealedCharacterHint) {
                        ClueText("Character: ${uiState.currentMovie?.characterHint}")
                    }
                    if (uiState.revealedSceneHint) {
                        ClueText("Scene: ${uiState.currentMovie?.sceneHint}")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Keyboard
                val rows = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rows.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row.forEach { letter ->
                                val isGuessed = letter in uiState.guessedLetters
                                val isCorrect = if (isGuessed) letter in (uiState.currentMovie?.title?.uppercase() ?: "") else null
                                KeyboardKey(
                                    letter = letter,
                                    isGuessed = isGuessed,
                                    isCorrect = isCorrect,
                                    onClick = { viewModel.guessLetter(letter) },
                                    modifier = Modifier.weight(1f, fill = false).widthIn(min = 32.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        }
    }
}

@Composable
fun ClueText(text: String) {
    Text(
        text = text,
        color = NeonCyan,
        fontSize = 14.sp,
        modifier = Modifier.padding(vertical = 4.dp).background(NeonCyan.copy(alpha=0.1f), RoundedCornerShape(4.dp)).padding(8.dp).fillMaxWidth()
    )
}

@Composable
fun HintButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    cost: Int,
    isRevealed: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            enabled = !isRevealed,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = NeonPink
            ),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(icon, contentDescription = label)
        }
        Spacer(Modifier.height(4.dp))
        if (!isRevealed) {
            if (cost == 0) {
                Text("FREE", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = "Coins", tint = Color(0xFFFFD700), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("$cost", color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Text("Revealed", color = NeonCyan, fontSize = 12.sp)
        }
    }
}
