package com.gratia.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.gratia.music.data.SettingsDataStore
import com.gratia.music.data.repository.ArtistInfo
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/**
 * Full page Artist Information Screen displayed over the ExpandedPlayer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistInfoScreen(
    artistName: String,
    artistInfo: ArtistInfo?,
    trackCredits: List<com.gratia.music.data.repository.ContributorInfo> = emptyList(),
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = GratiaTheme.motion
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    val settingsDataStore = remember { SettingsDataStore(context) }
    val onlineDataEnabled by settingsDataStore.onlineDataEnabledFlow.collectAsState(initial = true)
    
    var showEditDialog by remember { mutableStateOf(false) }
    var editBioText by remember { mutableStateOf("") }
    
    // Independent swipe-down-to-dismiss gesture for this full-screen layer
    val dismissOffsetY = remember { Animatable(0f) }
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .graphicsLayer {
                translationY = dismissOffsetY.value
                alpha = if (dismissOffsetY.value > 0f) {
                    (1f - (dismissOffsetY.value / 1000f)).coerceAtLeast(0.5f)
                } else 1f
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (dismissOffsetY.value > 300f) {
                                onDismiss()
                            } else {
                                dismissOffsetY.animateTo(0f, animationSpec = motion.springStiff())
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            dismissOffsetY.animateTo(0f, animationSpec = motion.springStiff())
                        }
                    },
                    onVerticalDrag = { _, dragAmount ->
                        // Only allow dragging down if we are at the top of the scroll view
                        if (scrollState.value == 0 && dragAmount > 0 || dismissOffsetY.value > 0) {
                            scope.launch {
                                dismissOffsetY.snapTo((dismissOffsetY.value + dragAmount).coerceAtLeast(0f))
                            }
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 64.dp)
        ) {
            // Header: Large Image + Name with Parallax effect
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f) // Square hero image
                    .clipToBounds()
            ) {
                // Background image with parallax
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Translate Y by half the scroll offset for a smooth parallax effect
                            val offset = scrollState.value * 0.5f
                            translationY = offset
                            // Slightly zoom out as we scroll down
                            val scale = (1f - (scrollState.value * 0.0005f)).coerceAtLeast(0.9f)
                            scaleX = scale
                            scaleY = scale
                        }
                ) {
                    if (artistInfo?.pictureUrl != null) {
                        AsyncImage(
                            model = artistInfo.pictureUrl,
                            contentDescription = "Artist Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(GratiaTheme.colors.surfaceHover)
                        )
                    }
                }

                // Gradient overlay blending smoothly into the background color
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.5f to Color.Transparent,
                                0.7f to GratiaTheme.colors.background.copy(alpha = 0.8f),
                                0.9f to GratiaTheme.colors.background,
                                1.0f to GratiaTheme.colors.background
                            )
                        )
                )

                // Name and Verification (Bottom Left)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = artistName,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 40.sp,
                            color = GratiaTheme.colors.textPrimary,
                            lineHeight = 44.sp
                        )
                        if (artistInfo?.isVerified == true && onlineDataEnabled) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = VerifiedRosette,
                                    contentDescription = "Verified Artist",
                                    tint = GratiaTheme.colors.accent,
                                    modifier = Modifier.matchParentSize()
                                )
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    if (artistInfo != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val formattedFans = NumberFormat.getNumberInstance(Locale.getDefault()).format(artistInfo.fanCount)
                        Text(
                            text = "$formattedFans Monthly Listeners",
                            fontFamily = Inter,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = GratiaTheme.colors.textSecondary
                        )
                    }
                }
            }

            // Body: Bio and Info
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                val bio = artistInfo?.biography
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "About",
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = GratiaTheme.colors.textPrimary
                    )
                    
                    if (!onlineDataEnabled) {
                        IconButton(onClick = { 
                            editBioText = bio ?: ""
                            showEditDialog = true 
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Biography", tint = GratiaTheme.colors.textPrimary)
                        }
                    }
                }

                if (!bio.isNullOrBlank() || !onlineDataEnabled) {
                    Text(
                        text = if (bio.isNullOrBlank()) "No biography available." else bio,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Normal,
                        fontSize = 16.sp,
                        color = GratiaTheme.colors.textSecondary,
                        lineHeight = 26.sp
                    )
                }

                // Credits Section
                if (trackCredits.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(40.dp))
                    Text(
                        text = "Credits",
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = GratiaTheme.colors.textPrimary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        trackCredits.forEach { credit ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(GratiaTheme.colors.surface)
                                    .padding(16.dp)
                            ) {
                                if (credit.pictureUrl != null) {
                                    AsyncImage(
                                        model = credit.pictureUrl,
                                        contentDescription = credit.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(GratiaTheme.colors.surfaceHover)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(GratiaTheme.colors.surfaceHover),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = credit.name.firstOrNull()?.toString() ?: "?",
                                            color = GratiaTheme.colors.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = credit.name,
                                        fontFamily = Inter,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = GratiaTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = credit.role,
                                        fontFamily = Inter,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = GratiaTheme.colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Sticky Top Bar (Back Button & Scrolled Title)
        val showTopBar = scrollState.value > 600
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (showTopBar) GratiaTheme.colors.background.copy(alpha = 0.9f) else Color.Transparent)
                .padding(top = 48.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (showTopBar) Color.Transparent else GratiaTheme.colors.background.copy(alpha = 0.4f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = GratiaTheme.colors.textPrimary
                    )
                }
                
                androidx.compose.animation.AnimatedVisibility(
                    visible = showTopBar,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { 20 }),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { 20 })
                ) {
                    Text(
                        text = artistName,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = GratiaTheme.colors.textPrimary,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }
    }

    // Edit Biography Dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Local Biography", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, color = GratiaTheme.colors.textPrimary) },
            text = {
                TextField(
                    value = editBioText,
                    onValueChange = { editBioText = it },
                    placeholder = { Text("Enter a local biography override...", fontFamily = Inter) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 250.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newBio = editBioText.trim().takeIf { it.isNotEmpty() }
                    
                    val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                    appScope.launch {
                        com.gratia.music.data.repository.ArtistInfoRepository.updateLocalBiography(artistName, newBio)
                    }
                    showEditDialog = false
                }) {
                    Text("Save", color = GratiaTheme.colors.accent, fontFamily = Inter, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = GratiaTheme.colors.textSecondary, fontFamily = Inter)
                }
            },
            containerColor = GratiaTheme.colors.surface
        )
    }
}
