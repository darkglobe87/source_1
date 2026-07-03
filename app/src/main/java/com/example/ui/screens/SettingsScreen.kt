package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.HapticsManager
import com.example.audio.MusicManager
import com.example.audio.SfxManager
import com.example.data.AppRepository
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: AppRepository,
    onBack: () -> Unit
) {
    val userState by repository.userState.collectAsState(initial = null)
    val isMusicMuted by MusicManager.isMuted.collectAsState()
    val isSfxEnabled by SfxManager.enabled.collectAsState()
    val isHapticsEnabled by HapticsManager.enabled.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SETTINGS", fontWeight = FontWeight.Black, color = NeonCyan) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            Text("Audio & Feedback", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))

            SettingsToggleRow(
                icon = { Icon(Icons.Default.VolumeUp, contentDescription = null, tint = NeonCyan) },
                label = "Music",
                checked = !isMusicMuted,
                onCheckedChange = { MusicManager.toggleMute() }
            )
            Spacer(Modifier.height(8.dp))
            SettingsToggleRow(
                icon = { Icon(Icons.Default.VolumeUp, contentDescription = null, tint = NeonPink) },
                label = "Sound effects",
                checked = isSfxEnabled,
                onCheckedChange = { SfxManager.toggleEnabled() }
            )
            Spacer(Modifier.height(8.dp))
            SettingsToggleRow(
                icon = { Icon(Icons.Default.Vibration, contentDescription = null, tint = NeonPurple) },
                label = "Haptics",
                checked = isHapticsEnabled,
                onCheckedChange = { HapticsManager.toggleEnabled() }
            )

            Spacer(Modifier.height(32.dp))
            Text("Stats", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StatRow(icon = Icons.Default.LocalFireDepartment, iconTint = androidx.compose.ui.graphics.Color(0xFFFF7A00), label = "Current streak", value = "${userState?.currentStreak ?: 0}")
                    Spacer(Modifier.height(8.dp))
                    StatRow(icon = Icons.Default.LocalFireDepartment, iconTint = NeonPink, label = "Best streak", value = "${userState?.bestStreak ?: 0}")
                    Spacer(Modifier.height(8.dp))
                    StatRow(icon = Icons.Default.Star, iconTint = androidx.compose.ui.graphics.Color(0xFFFFD700), label = "Movies guessed", value = "${userState?.totalMoviesGuessed ?: 0}")
                }
            }

            Spacer(Modifier.height(32.dp))

            OutlinedButton(
                onClick = {
                    val packageName = context.packageName
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
                    } catch (e: ActivityNotFoundException) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                    }
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Rate Bad Plots")
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: @Composable () -> Unit,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.width(12.dp))
                Text(label, color = MaterialTheme.colorScheme.onSurface)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun StatRow(icon: androidx.compose.ui.graphics.vector.ImageVector, iconTint: androidx.compose.ui.graphics.Color, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}
