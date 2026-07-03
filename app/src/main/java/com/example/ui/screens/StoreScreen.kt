package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppRepository
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(
    repository: AppRepository,
    onBack: () -> Unit
) {
    val userState by repository.userState.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val snackbarHostState = androidx.compose.runtime.remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("STORE", fontWeight = FontWeight.Black, color = NeonPink) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = "Coins", tint = Color(0xFFFFD700), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${userState?.coins ?: 0}", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            StoreItem(
                title = "Buy 100 Coins",
                description = "Get extra coins for hints.",
                price = "£0.99",
                onClick = {
                    scope.launch { 
                        repository.addCoins(100) 
                        snackbarHostState.showSnackbar("Purchased 100 Coins!")
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            StoreItem(
                title = "Buy 500 Coins",
                description = "Best value for coins.",
                price = "£3.99",
                onClick = {
                    scope.launch { 
                        repository.addCoins(500) 
                        snackbarHostState.showSnackbar("Purchased 500 Coins!")
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (userState?.hasPurchasedAdFree == true) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NeonCyan.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonCyan)
                        Spacer(Modifier.width(16.dp))
                        Text("Ad-Free & Unlimited Hints Unlocked!", color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                StoreItem(
                    title = "Lifetime Premium",
                    description = "Ad-free experience & unlimited hints forever.",
                    price = "£5.00",
                    isPremium = true,
                    onClick = {
                        scope.launch {
                            repository.purchaseAdFree()
                            repository.addCoins(9999)
                            snackbarHostState.showSnackbar("Premium Unlocked!")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun StoreItem(
    title: String,
    description: String,
    price: String,
    isPremium: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPremium) NeonPurple.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (isPremium) NeonCyan else MaterialTheme.colorScheme.onSurface)
                Text(description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(16.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = if (isPremium) NeonPink else NeonPurple)
            ) {
                Text(price, fontWeight = FontWeight.Bold)
            }
        }
    }
}
