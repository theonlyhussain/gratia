package com.gratia.music.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.data.CacheManager
import com.gratia.music.data.SettingsDataStore
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cacheManager = remember { CacheManager(context) }
    val settingsDataStore = remember { SettingsDataStore(context) }
    
    var artworkCacheSize by remember { mutableStateOf(0L) }
    var coilCacheSize by remember { mutableStateOf(0L) }
    
    val cacheLimitMb by settingsDataStore.cacheLimitMbFlow.collectAsState(initial = 500)

    fun refreshSizes() {
        scope.launch {
            artworkCacheSize = cacheManager.getArtworkCacheSize()
            coilCacheSize = cacheManager.getCoilCacheSize()
        }
    }

    LaunchedEffect(Unit) {
        refreshSizes()
    }

    var showClearCacheDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(GratiaTheme.colors.surface)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.size(16.dp), tint = GratiaTheme.colors.textSecondary)
            }
            Column {
                Text("Storage & Cache", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = GratiaTheme.colors.textPrimary)
                Text("Manage space used by Gratia", fontFamily = Inter, fontSize = 12.sp, color = GratiaTheme.colors.textSecondary)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Storage cards
        Text("LOCAL STORAGE", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = GratiaTheme.colors.textSecondary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        
        StorageCard(
            icon = Icons.Default.PhoneAndroid,
            title = "Local Device",
            subtitle = "Songs stored on this device",
            isActive = true,
            accentColor = GratiaTheme.colors.success,
            onClick = { }
        )

        Spacer(Modifier.height(32.dp))

        Text("CACHE MANAGEMENT", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = GratiaTheme.colors.textSecondary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))

        val totalCache = artworkCacheSize + coilCacheSize
        val formattedCache = Formatter.formatFileSize(context, totalCache)

        StorageCard(
            icon = Icons.Default.Cached,
            title = "App Cache",
            subtitle = "Artwork and temporary data ($formattedCache)",
            isActive = false,
            accentColor = GratiaTheme.colors.accent,
            onClick = { showClearCacheDialog = true }
        )

        Spacer(Modifier.height(16.dp))
        
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text("Max Cache Size", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = GratiaTheme.colors.textPrimary)
            Text("Limit how much space Gratia can use for artwork.", fontFamily = Inter, fontSize = 12.sp, color = GratiaTheme.colors.textSecondary)
            
            Slider(
                value = cacheLimitMb.toFloat(),
                onValueChange = { scope.launch { settingsDataStore.setCacheLimitMb(it.toInt()) } },
                valueRange = 100f..2000f,
                steps = 18,
                colors = SliderDefaults.colors(
                    thumbColor = GratiaTheme.colors.accent,
                    activeTrackColor = GratiaTheme.colors.accent
                )
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("100 MB", fontSize = 12.sp, color = GratiaTheme.colors.textSecondary)
                Text("${cacheLimitMb} MB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GratiaTheme.colors.textPrimary)
                Text("2 GB", fontSize = 12.sp, color = GratiaTheme.colors.textSecondary)
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Cache?", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold) },
            text = { Text("This will remove all downloaded artwork and temporary files. They will be re-downloaded as needed. This won't delete your songs.", fontFamily = Inter) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        cacheManager.clearAllCaches()
                        refreshSizes()
                        showClearCacheDialog = false
                    }
                }) {
                    Text("Clear", color = GratiaTheme.colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel", color = GratiaTheme.colors.textPrimary)
                }
            },
            containerColor = GratiaTheme.colors.surface,
            titleContentColor = GratiaTheme.colors.textPrimary,
            textContentColor = GratiaTheme.colors.textSecondary
        )
    }
}

@Composable
private fun StorageCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isActive: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = GratiaTheme.colors.surface,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.1f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = accentColor, modifier = Modifier.size(22.dp))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = GratiaTheme.colors.textPrimary)
                Text(subtitle, fontFamily = Inter, fontSize = 12.sp, color = GratiaTheme.colors.textSecondary)
            }

            if (isActive) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.15f),
                ) {
                    Text("Active", fontFamily = Inter, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        color = accentColor, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            } else {
                Icon(Icons.Default.ChevronRight, null, tint = GratiaTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
