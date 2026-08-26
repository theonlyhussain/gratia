package com.gratia.music.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.UserProfileEntity
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.ui.components.AppleLargeTitleHeader
import com.gratia.music.ui.components.bounceClick
import com.gratia.music.ui.components.clickableWithScale
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun YouScreen(
    onNavigateBack: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToListeningHistory: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToSmartUpdate: () -> Unit,
    onNavigateToLibrarySettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToSongs: () -> Unit,
    onNavigateToAlbums: () -> Unit,
    onNavigateToArtists: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToPlayback: () -> Unit
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    val profileDao = remember { GratiaApp.instance.database.userProfileDao() }
    val playlistDao = remember { GratiaApp.instance.database.playlistDao() }
    
    val songCount by songRepo.getSongCount().collectAsState(initial = 0)
    val allSongs by songRepo.getAllSongs().collectAsState(initial = emptyList())
    val playlists by playlistDao.getAllPlaylists().collectAsState(initial = emptyList())
    
    val albumCount = remember(allSongs) { allSongs.mapNotNull { it.album }.distinct().size }
    val artistCount = remember(allSongs) { allSongs.map { it.artist }.distinct().size }

    // Settings state
    val settingsDataStore = remember { com.gratia.music.data.SettingsDataStore(context) }
    val smartUpdateEnabled by settingsDataStore.smartUpdateEnabledFlow.collectAsState(initial = false)
    val onlineDataEnabled by settingsDataStore.onlineDataEnabledFlow.collectAsState(initial = true)
    val updateState by GratiaApp.instance.updateManager.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Profile state
    val profileFlow by profileDao.getProfile().collectAsState(initial = null)
    var displayName by remember { mutableStateOf("Music Lover") }
    var avatarPath by remember { mutableStateOf<String?>(null) }
    var bannerPath by remember { mutableStateOf<String?>(null) }
    var hasChanges by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var saveSuccess by remember { mutableStateOf(false) }
    
    var showOnlineDataWarning by remember { mutableStateOf(false) }

    val versionName = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName
        } catch (e: Exception) {
            "Unknown"
        }
    }

    // Load profile data
    LaunchedEffect(profileFlow) {
        val profile = profileFlow
        if (profile != null && !hasChanges) {
            displayName = profile.displayName
            avatarPath = profile.avatarPath
            bannerPath = profile.bannerPath
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) {
                val timestamp = System.currentTimeMillis()
                val file = File(context.filesDir, "profile_avatar_$timestamp.jpg")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    val currentDbAvatar = profileFlow?.avatarPath
                    if (avatarPath != null && avatarPath != currentDbAvatar) {
                        File(avatarPath!!).delete()
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
                val timestamp = System.currentTimeMillis()
                val file = File(context.filesDir, "profile_banner_$timestamp.jpg")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    val currentDbBanner = profileFlow?.bannerPath
                    if (bannerPath != null && bannerPath != currentDbBanner) {
                        File(bannerPath!!).delete()
                    }
                    bannerPath = file.absolutePath
                    hasChanges = true
                } catch (_: Exception) {}
            }
        }
    }

    val bottomInset = com.gratia.music.ui.LocalBottomPadding.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(bottom = bottomInset + 32.dp),
    ) {
        AppleLargeTitleHeader(
            title = "You",
            onBack = onNavigateBack
        )

        // Banner image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(enabled = isEditing) { bannerPicker.launch(arrayOf("image/*")) }
        ) {
            if (bannerPath != null && File(bannerPath!!).exists()) {
                AsyncImage(
                    model = File(bannerPath!!),
                    contentDescription = "Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
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
                    if (isEditing) {
                        Icon(Icons.Default.CameraAlt, null, tint = GratiaTheme.colors.background.copy(alpha = 0.4f), modifier = Modifier.size(28.dp))
                    }
                }
            }
            com.gratia.music.ui.components.GratiaEditAffordance(
                isEditing = isEditing,
                onToggle = { isEditing = !isEditing },
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }

        // Avatar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-32).dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .size(80.dp)
                    .clickable(enabled = isEditing) { avatarPicker.launch(arrayOf("image/*")) },
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

        AnimatedVisibility(
            visible = isEditing && (avatarPath != null || bannerPath != null),
            modifier = Modifier.offset(y = (-24).dp),
            enter = fadeIn() + expandVertically(animationSpec = spring(stiffness = 300f)),
            exit = fadeOut() + shrinkVertically(animationSpec = spring(stiffness = 300f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                if (avatarPath != null) {
                    TextButton(onClick = { avatarPath = null; hasChanges = true }) {
                        Text("Remove Picture", fontFamily = Inter, fontSize = 12.sp, color = GratiaTheme.colors.error)
                    }
                }
                if (avatarPath != null && bannerPath != null) {
                    Spacer(modifier = Modifier.width(16.dp))
                }
                if (bannerPath != null) {
                    TextButton(onClick = { bannerPath = null; hasChanges = true }) {
                        Text("Remove Cover", fontFamily = Inter, fontSize = 12.sp, color = GratiaTheme.colors.error)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !isEditing || (avatarPath == null && bannerPath == null),
            modifier = Modifier.offset(y = (-24).dp),
            enter = expandVertically(animationSpec = spring(stiffness = 300f)),
            exit = shrinkVertically(animationSpec = spring(stiffness = 300f))
        ) {
             Spacer(Modifier.height(24.dp))
        }

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

            AnimatedVisibility(
                visible = hasChanges && displayName.isNotBlank(),
                enter = expandVertically(animationSpec = spring(stiffness = 400f)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(stiffness = 400f)) + fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(12.dp))
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
                                context.getSharedPreferences("gratia_profile", android.content.Context.MODE_PRIVATE)
                                    .edit()
                                    .putString("display_name", profile.displayName)
                                    .apply()
                                
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                saveSuccess = true
                                delay(2000)
                                hasChanges = false
                                saveSuccess = false
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (saveSuccess) GratiaTheme.colors.success else GratiaTheme.colors.accent,
                            contentColor = GratiaTheme.colors.background,
                            disabledContainerColor = GratiaTheme.colors.surfaceHover,
                            disabledContentColor = GratiaTheme.colors.textSecondary
                        ),
                        modifier = Modifier.fillMaxWidth(0.6f).height(44.dp)
                    ) {
                        AnimatedContent(
                            targetState = saveSuccess,
                            transitionSpec = {
                                (scaleIn(initialScale = 0.5f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f)) + fadeIn()) togetherWith 
                                (scaleOut(targetScale = 0.8f) + fadeOut())
                            },
                            label = "SaveButtonAnimation"
                        ) { isSuccess ->
                            if (isSuccess) {
                                Icon(Icons.Default.Check, contentDescription = "Saved", modifier = Modifier.size(20.dp))
                            } else {
                                Text("Save", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // LIBRARY
        SectionTitle("LIBRARY")
        SectionCard {
            SettingsRow(icon = Icons.Default.Storage, iconBg = Color(0xFF8E8E93), title = "Storage", subtitle = "Local Device", onClick = onNavigateToStorage)
            SettingsDivider()
            SettingsRow(icon = Icons.Default.LibraryMusic, iconBg = Color(0xFF5856D6), title = "Songs", subtitle = "$songCount tracks", onClick = onNavigateToSongs)
            SettingsDivider()
            SettingsRow(icon = Icons.Default.Album, iconBg = Color(0xFFFF2D55), title = "Albums", subtitle = "$albumCount albums", onClick = onNavigateToAlbums)
            SettingsDivider()
            SettingsRow(icon = Icons.Default.Person, iconBg = Color(0xFFFF9500), title = "Artists", subtitle = "$artistCount artists", onClick = onNavigateToArtists)
            SettingsDivider()
            SettingsRow(icon = Icons.Default.QueueMusic, iconBg = Color(0xFF34C759), title = "Playlists", subtitle = "${playlists.size} playlists", onClick = onNavigateToPlaylists)
        }
        Spacer(Modifier.height(20.dp))

        // PLAYBACK
        SectionTitle("PLAYBACK")
        SectionCard {
            SettingsRow(
                icon = Icons.Default.Speaker,
                iconBg = Color(0xFF007AFF),
                title = "Playback",
                subtitle = "Crossfade",
                onClick = onNavigateToPlayback
            )
            SettingsDivider()
            SettingsRow(icon = Icons.Default.BarChart, iconBg = Color(0xFF007AFF), title = "Listening History", subtitle = "Your listening statistics", onClick = onNavigateToListeningHistory)
        }
        Spacer(Modifier.height(20.dp))

        // SETTINGS
        SectionTitle("SETTINGS")
        SectionCard {
            SettingsRow(icon = Icons.Default.Palette, iconBg = Color(0xFFFF9500), title = "Appearance", subtitle = "Theme, OLED", onClick = onNavigateToAppearance)
            SettingsDivider()
            SettingsRow(icon = Icons.Default.GraphicEq, iconBg = Color(0xFFFF2D55), title = "Equalizer", subtitle = "Audio effects & frequencies", onClick = onNavigateToEqualizer)
            SettingsDivider()
            SettingsRow(
                icon = Icons.Default.Update, 
                iconBg = Color(0xFF34C759), 
                title = "Smart Update", 
                subtitle = if (smartUpdateEnabled) "Enabled" else "Disabled", 
                onClick = onNavigateToSmartUpdate,
                badge = if (!smartUpdateEnabled) "Recommended" else null,
                showDot = updateState is com.gratia.music.updater.UpdateState.UpdateAvailable || updateState is com.gratia.music.updater.UpdateState.ReadyToInstall
            )
            SettingsDivider()
            SettingsRow(icon = Icons.Default.Sync, iconBg = Color(0xFF5856D6), title = "Library", subtitle = "Local files & sync", onClick = onNavigateToLibrarySettings)
        }
        Spacer(Modifier.height(20.dp))

        // ABOUT
        SectionTitle("ABOUT")
        SectionCard {
            SettingsRow(icon = Icons.Default.PrivacyTip, iconBg = Color(0xFF8E8E93), title = "Privacy", subtitle = "All data stays on your device", onClick = {}, showChevron = false)
            SettingsDivider()
            SettingsRow(
                icon = Icons.Default.CloudQueue, 
                iconBg = Color(0xFF32ADE6), 
                title = "Online Data", 
                subtitle = if (onlineDataEnabled) "Enabled (APIs active)" else "Disabled (Local only)", 
                onClick = { 
                    if (onlineDataEnabled) {
                        showOnlineDataWarning = true
                    } else {
                        coroutineScope.launch { settingsDataStore.setOnlineDataEnabled(true) } 
                    }
                }, 
                showChevron = false
            )
            SettingsDivider()
            SettingsRow(icon = Icons.Default.Info, iconBg = Color(0xFF007AFF), title = "About Gratia", subtitle = "Version $versionName", onClick = onNavigateToAbout, showChevron = true)
        }
        
        Spacer(Modifier.height(56.dp))
    }

    if (showOnlineDataWarning) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showOnlineDataWarning = false },
            title = { androidx.compose.material3.Text("Disable Online Data?", fontFamily = com.gratia.music.ui.theme.SpaceGrotesk, fontWeight = FontWeight.Bold, color = GratiaTheme.colors.textPrimary) },
            text = { 
                androidx.compose.material3.Text(
                    "Are you sure you want to disable Online Data? Gratia will no longer fetch missing artist images, biographies, or lyrics from the internet. You will only see what is already stored locally on your device.",
                    fontFamily = com.gratia.music.ui.theme.Inter,
                    color = GratiaTheme.colors.textSecondary
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    coroutineScope.launch { settingsDataStore.setOnlineDataEnabled(false) }
                    showOnlineDataWarning = false
                }) {
                    androidx.compose.material3.Text("Disable", color = Color(0xFFFF3B30), fontFamily = com.gratia.music.ui.theme.Inter, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showOnlineDataWarning = false }) {
                    androidx.compose.material3.Text("Cancel", color = GratiaTheme.colors.textSecondary, fontFamily = com.gratia.music.ui.theme.Inter)
                }
            },
            containerColor = GratiaTheme.colors.surface
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title, 
        fontFamily = Inter, 
        fontWeight = FontWeight.SemiBold, 
        fontSize = 11.sp,
        color = GratiaTheme.colors.textSecondary, 
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
    )
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GratiaTheme.colors.surface),
        content = content
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    badge: String? = null,
    showDot: Boolean = false,
    showChevron: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableWithScale(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            GratiaText(
                text = title,
                style = GratiaTheme.typography.body,
                color = GratiaTheme.colors.textPrimary
            )
            if (subtitle != null) {
                GratiaText(
                    text = subtitle,
                    style = GratiaTheme.typography.caption,
                    color = GratiaTheme.colors.textSecondary
                )
            }
        }

        if (badge != null) {
            GratiaText(
                text = badge,
                style = GratiaTheme.typography.caption,
                color = GratiaTheme.colors.accent
            )
            Spacer(Modifier.width(8.dp))
        }

        if (showDot) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(GratiaTheme.colors.error)
            )
            Spacer(Modifier.width(8.dp))
        }

        if (showChevron) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = GratiaTheme.colors.textSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 62.dp),
        thickness = 0.5.dp,
        color = GratiaTheme.colors.textSecondary.copy(alpha = 0.15f)
    )
}
