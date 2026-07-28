package com.gratia.music.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gratia.music.data.model.SongEntity
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistEditorSheet(
    initialSong: SongEntity? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, customImageUri: Uri?) -> Unit
) {
    var name by remember { mutableStateOf("New Playlist") }
    var description by remember { mutableStateOf("") }
    var customImageUri by remember { mutableStateOf<Uri?>(null) }
    
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> 
            if (uri != null) customImageUri = uri 
        }
    )

    // Extract dominant colors from initial song art
    var dominantColors by remember { mutableStateOf(listOf(Color(0xFF2C2C2E), Color(0xFF1C1C1E))) }
    LaunchedEffect(initialSong) {
        val path = initialSong?.coverArtPath
        if (!path.isNullOrBlank() && File(path).exists()) {
            withContext(Dispatchers.IO) {
                try {
                    val bitmap = BitmapFactory.decodeFile(path)
                    if (bitmap != null) {
                        val palette = Palette.from(bitmap).generate()
                        val colors = listOfNotNull(
                            palette.getVibrantColor(Color.Transparent.toArgb()),
                            palette.getDarkVibrantColor(Color.Transparent.toArgb()),
                            palette.getMutedColor(Color.Transparent.toArgb())
                        ).filter { it != Color.Transparent.toArgb() }.map { Color(it) }
                        
                        if (colors.isNotEmpty()) {
                            dominantColors = if (colors.size == 1) listOf(colors[0], colors[0].copy(alpha = 0.6f)) else colors
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Animated gradient
    val infiniteTransition = rememberInfiniteTransition(label = "gradient")
    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientOffset"
    )

    val pagerState = rememberPagerState(pageCount = { 5 })
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.95f),
        containerColor = GratiaTheme.colors.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = GratiaTheme.colors.glassBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cancel",
                    fontFamily = Inter,
                    color = GratiaTheme.colors.textSecondary,
                    fontSize = 16.sp,
                    modifier = Modifier.clickable { onDismiss() }.padding(8.dp)
                )
                Text(
                    text = "Done",
                    fontFamily = Inter,
                    color = GratiaTheme.colors.accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onSave(name, description, customImageUri) }.padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Cover Carousel
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                pageSpacing = 16.dp,
                contentPadding = PaddingValues(horizontal = 48.dp)
            ) { page ->
                val pageScale = if (pagerState.currentPage == page) 1f else 0.9f
                val pageAlpha = if (pagerState.currentPage == page) 1f else 0.5f

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            if (page == 0) {
                                photoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (page == 0) {
                        // Camera or Custom Image
                        if (customImageUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(customImageUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Custom Cover",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().background(GratiaTheme.colors.surfaceHover),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier.size(64.dp).clip(CircleShape).background(GratiaTheme.colors.accent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Pick Image",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    } else {
                        // Generated Gradients
                        val brush = when (page) {
                            1 -> Brush.linearGradient(dominantColors)
                            2 -> Brush.radialGradient(dominantColors, radius = 800f + animatedOffset)
                            3 -> Brush.sweepGradient(dominantColors)
                            4 -> Brush.verticalGradient(dominantColors.reversed())
                            else -> Brush.linearGradient(dominantColors)
                        }
                        Box(
                            modifier = Modifier.fillMaxSize().background(brush),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name.takeIf { it.isNotBlank() } ?: "Playlist",
                                fontFamily = SpaceGrotesk,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }

            // Pager Indicators
            Row(
                Modifier.height(24.dp).fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(5) { iteration ->
                    val color = if (pagerState.currentPage == iteration) GratiaTheme.colors.textPrimary else GratiaTheme.colors.surfaceHover
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(color)
                            .size(6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title Field
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                textStyle = TextStyle(
                    color = GratiaTheme.colors.textPrimary,
                    fontSize = 24.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                cursorBrush = SolidColor(GratiaTheme.colors.accent),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.Center) {
                        if (name.isEmpty()) {
                            Text(
                                text = "Playlist Name",
                                color = GratiaTheme.colors.textSecondary,
                                fontSize = 24.sp,
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        innerTextField()
                    }
                }
            )

            HorizontalDivider(color = GratiaTheme.colors.glassBorder)

            // Description Field
            BasicTextField(
                value = description,
                onValueChange = { description = it },
                textStyle = TextStyle(
                    color = GratiaTheme.colors.textPrimary,
                    fontSize = 16.sp,
                    fontFamily = Inter
                ),
                cursorBrush = SolidColor(GratiaTheme.colors.accent),
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (description.isEmpty()) {
                            Text(
                                text = "Add description",
                                color = GratiaTheme.colors.textSecondary,
                                fontSize = 16.sp,
                                fontFamily = Inter
                            )
                        }
                        innerTextField()
                    }
                }
            )

            HorizontalDivider(color = GratiaTheme.colors.glassBorder)
            
            Spacer(modifier = Modifier.height(16.dp))

            // Tracklist placeholder (just Add Music button for now as per Apple Music)
            if (initialSong != null) {
                SongRow(
                    song = initialSong,
                    index = 0,
                    isActive = false,
                    isPlaying = false,
                    onClick = {}
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Add Music Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { /* TODO: Open search */ }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = GratiaTheme.colors.surfaceHover
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Music",
                            tint = GratiaTheme.colors.textPrimary
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "Add Music",
                    fontFamily = Inter,
                    fontSize = 16.sp,
                    color = GratiaTheme.colors.textPrimary
                )
            }
        }
    }
}
