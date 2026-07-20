package com.gratia.music.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gratia.music.GratiaApp
import com.gratia.music.data.CoverArtManager
import com.gratia.music.data.model.UserProfileEntity
import com.gratia.music.data.repository.ListeningEventRepository
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.components.AppleLargeTitleHeader
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToStorage: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    val profileDao = remember { GratiaApp.instance.database.userProfileDao() }
    val listeningRepo = remember { ListeningEventRepository(GratiaApp.instance.database.listeningEventDao()) }
    val songCount by songRepo.getSongCount().collectAsState(initial = 0)
    val favCount by songRepo.getFavoritesCount().collectAsState(initial = 0)
    
    // Additional Stats
    val allSongs by songRepo.getAllSongs().collectAsState(initial = emptyList())
    val playlistDao = remember { GratiaApp.instance.database.playlistDao() }
    val playlists by playlistDao.getAllPlaylists().collectAsState(initial = emptyList())
    
    val albumCount = remember(allSongs) { allSongs.mapNotNull { it.album }.distinct().size }
    val artistCount = remember(allSongs) { allSongs.map { it.artist }.distinct().size }
    val totalListenTimeMs = remember(allSongs) { allSongs.sumOf { it.totalListenTime ?: 0L } }
    val listenMinutes = (totalListenTimeMs / (1000 * 60)).toInt()

    // Profile state
    val profileFlow by profileDao.getProfile().collectAsState(initial = null)
    var displayName by remember { mutableStateOf("Music Lover") }
    var avatarPath by remember { mutableStateOf<String?>(null) }
    var bannerPath by remember { mutableStateOf<String?>(null) }
    var hasChanges by remember { mutableStateOf(false) }
    var saveSuccess by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    // Load profile data
    LaunchedEffect(profileFlow) {
        val profile = profileFlow
        if (profile != null) {
            displayName = profile.displayName
            avatarPath = profile.avatarPath
            bannerPath = profile.bannerPath
        }
    }

    // Image pickers
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) {
                val file = File(context.filesDir, "profile_avatar.jpg")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    avatarPath = file.absolutePath
                    hasChanges = true
                } catch (_: Exception) {}
            }
        }
    }

    val bannerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) {
                val file = File(context.filesDir, "profile_banner.jpg")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    bannerPath = file.absolutePath
                    hasChanges = true
                } catch (_: Exception) {}
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        AppleLargeTitleHeader(
            title = "Profile",
            onBack = onNavigateBack
        )

        // Banner image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { bannerPicker.launch(arrayOf("image/*")) }
        ) {
            if (bannerPath != null && File(bannerPath!!).exists()) {
                AsyncImage(
                    model = File(bannerPath!!),
                    contentDescription = "Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Gratia gradient fallback banner
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    GratiaTheme.colors.textPrimary,
                                    GratiaTheme.colors.accent,
                                    GratiaTheme.colors.accent.copy(alpha = 0.8f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, null, tint = GratiaTheme.colors.background.copy(alpha = 0.4f), modifier = Modifier.size(28.dp))
                }
            }
        }

        // Avatar overlapping banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-32).dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .size(80.dp)
                    .clickable { avatarPicker.launch(arrayOf("image/*")) },
                shape = CircleShape,
                color = GratiaTheme.colors.surface,
                shadowElevation = 4.dp,
            ) {
                if (avatarPath != null && File(avatarPath!!).exists()) {
                    AsyncImage(
                        model = File(avatarPath!!),
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            displayName.take(1).uppercase(),
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            color = GratiaTheme.colors.accent
                        )
                    }
                }
            }
        }

        // Remove picture option
        if (avatarPath != null) {
            Column(
                modifier = Modifier.fillMaxWidth().offset(y = (-24).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextButton(
                    onClick = {
                        avatarPath = null
                        hasChanges = true
                    }
                ) {
                    Text("Remove Picture", fontFamily = Inter, fontSize = 12.sp, color = GratiaTheme.colors.error)
                }
            }
        }

        // Display name field
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .offset(y = (-16).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Display Name", fontFamily = Inter, fontSize = 11.sp, color = GratiaTheme.colors.textSecondary)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = displayName,
                onValueChange = {
                    displayName = it
                    hasChanges = true
                    saveSuccess = false
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GratiaTheme.colors.accent.copy(alpha = 0.5f),
                    unfocusedBorderColor = GratiaTheme.colors.surfaceHover,
                    focusedContainerColor = GratiaTheme.colors.surface,
                    unfocusedContainerColor = GratiaTheme.colors.surface,
                    focusedTextColor = GratiaTheme.colors.textPrimary,
                    unfocusedTextColor = GratiaTheme.colors.textPrimary,
                    cursorColor = GratiaTheme.colors.accent,
                )
            )

            Spacer(Modifier.height(12.dp))

            // Save button
            Button(
                onClick = {
                    scope.launch {
                        val profile = UserProfileEntity(
                            displayName = displayName.trim().ifBlank { "Music Lover" },
                            avatarPath = avatarPath,
                            bannerPath = bannerPath,
                            updatedAt = System.currentTimeMillis()
                        )
                        profileDao.upsertProfile(profile)
                        // Also save to SharedPrefs for HomeScreen access
                        context.getSharedPreferences("gratia_profile", android.content.Context.MODE_PRIVATE)
                            .edit()
                            .putString("display_name", profile.displayName)
                            .apply()
                        hasChanges = false
                        saveSuccess = true
                    }
                },
                enabled = hasChanges && displayName.isNotBlank(),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GratiaTheme.colors.accent,
                    contentColor = GratiaTheme.colors.background,
                    disabledContainerColor = GratiaTheme.colors.surfaceHover,
                    disabledContentColor = GratiaTheme.colors.textSecondary
                ),
                modifier = Modifier.fillMaxWidth(0.6f).height(44.dp)
            ) {
                Text("Save", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            if (saveSuccess) {
                Spacer(Modifier.height(8.dp))
                Text("Profile saved!", fontFamily = Inter, fontSize = 12.sp, color = GratiaTheme.colors.success)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Stats row
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            color = GratiaTheme.colors.surface,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(count = "$songCount", label = "Songs")
                StatItem(count = "$favCount", label = "Favorites")
                StatItem(count = "Local", label = "Storage")
            }
        }

        Spacer(Modifier.height(24.dp))

        // Settings sections
        ProfileSection(title = "LIBRARY") {
            ProfileItem(icon = Icons.Default.Storage, label = "Storage", detail = "Local Device", onClick = onNavigateToStorage)
            ProfileItem(icon = Icons.Default.LibraryMusic, label = "Songs", detail = "$songCount tracks")
            ProfileItem(icon = Icons.Default.Album, label = "Albums", detail = "$albumCount albums")
            ProfileItem(icon = Icons.Default.Person, label = "Artists", detail = "$artistCount artists")
            ProfileItem(icon = Icons.Default.QueueMusic, label = "Playlists", detail = "${playlists.size} playlists")
        }

        ProfileSection(title = "PLAYBACK") {
            ProfileItem(icon = Icons.Default.Headset, label = "Total Listening Time", detail = "$listenMinutes minutes")
            ProfileItem(icon = Icons.Default.HighQuality, label = "Audio Quality", detail = "Original quality preserved")
            ProfileItem(icon = Icons.Default.Lyrics, label = "Lyrics", detail = "Plain & synced support")
        }

        ProfileSection(title = "ABOUT") {
            ProfileItem(icon = Icons.Default.Palette, label = "Appearance", detail = "Gratia Warm Premium")
            ProfileItem(icon = Icons.Default.PrivacyTip, label = "Privacy", detail = "All data stays on your device")
            ProfileItem(icon = Icons.Default.CalendarMonth, label = "Clear Listening History", detail = "Remove local calendar data", onClick = { showClearHistoryDialog = true })
            ProfileItem(icon = Icons.Default.Info, label = "About Gratia", detail = "Version 1.0.0")
        }

        Spacer(Modifier.height(32.dp))

        // Footer
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Gratia", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GratiaTheme.colors.accent)
            Text("Your personal music library", fontFamily = Inter, fontSize = 11.sp, color = GratiaTheme.colors.textSecondary)
        }

        Spacer(Modifier.height(40.dp))
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear listening history?", color = GratiaTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("This removes local listening stats from this device.", color = GratiaTheme.colors.textSecondary) },
            containerColor = GratiaTheme.colors.surface,
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        listeningRepo.clearHistory()
                    }
                    showClearHistoryDialog = false
                }) {
                    Text("Clear", color = GratiaTheme.colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel", color = GratiaTheme.colors.textPrimary)
                }
            }
        )
    }
}

@Composable
private fun StatItem(count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = GratiaTheme.colors.textPrimary)
        Text(label, fontFamily = Inter, fontSize = 11.sp, color = GratiaTheme.colors.textSecondary)
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
        color = GratiaTheme.colors.textSecondary, letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        color = GratiaTheme.colors.surface,
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ProfileItem(
    icon: ImageVector,
    label: String,
    detail: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = GratiaTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontFamily = Inter, fontSize = 14.sp, color = GratiaTheme.colors.textPrimary)
            if (detail != null) {
                Text(detail, fontFamily = Inter, fontSize = 11.sp, color = GratiaTheme.colors.textSecondary)
            }
        }
        if (onClick != null) {
            Icon(Icons.Default.ChevronRight, null, tint = GratiaTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
}
