package com.gratia.music.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.gratia.music.R
import com.gratia.music.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GratiaMusicWidget : GlanceAppWidget() {

    companion object {
        val KEY_SONG_TITLE = stringPreferencesKey("song_title")
        val KEY_ARTIST_NAME = stringPreferencesKey("artist_name")
        val KEY_COVER_PATH = stringPreferencesKey("cover_path")
        val KEY_IS_PLAYING = booleanPreferencesKey("is_playing")
    }

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }

    @Composable
    private fun WidgetContent() {
        val prefs = currentState<Preferences>()
        val title = prefs[KEY_SONG_TITLE] ?: "Not Playing"
        val artist = prefs[KEY_ARTIST_NAME] ?: "Gratia"
        val coverPath = prefs[KEY_COVER_PATH]
        val isPlaying = prefs[KEY_IS_PLAYING] ?: false

        val context = LocalContext.current
        val size = LocalSize.current

        var bitmap by remember { mutableStateOf<Bitmap?>(null) }

        LaunchedEffect(coverPath) {
            if (!coverPath.isNullOrEmpty()) {
                bitmap = withContext(Dispatchers.IO) {
                    try {
                        BitmapFactory.decodeFile(coverPath)
                    } catch (e: Exception) {
                        null
                    }
                }
            } else {
                bitmap = null
            }
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(24.dp)
                .clickable(actionStartActivity<MainActivity>())
                .padding(12.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art
                if (bitmap != null) {
                    Image(
                        provider = ImageProvider(bitmap!!),
                        contentDescription = "Cover",
                        contentScale = ContentScale.Crop,
                        modifier = GlanceModifier
                            .size(if (size.height < 100.dp) 64.dp else 80.dp)
                            .cornerRadius(12.dp)
                    )
                } else {
                    Box(
                        modifier = GlanceModifier
                            .size(if (size.height < 100.dp) 64.dp else 80.dp)
                            .background(Color.DarkGray)
                            .cornerRadius(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(android.R.drawable.ic_media_play),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(ColorProvider(Color.White))
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.width(12.dp))

                // Text details
                Column(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = title,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = artist,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 14.sp
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                }

                Spacer(modifier = GlanceModifier.width(8.dp))

                // Playback Controls
                Row(
                    modifier = GlanceModifier.fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (size.width >= 250.dp) {
                        Image(
                            provider = ImageProvider(android.R.drawable.ic_media_previous),
                            contentDescription = "Previous",
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
                            modifier = GlanceModifier
                                .size(36.dp)
                                .clickable(actionRunCallback<SkipPreviousAction>())
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                    }

                    Image(
                        provider = ImageProvider(
                            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                        ),
                        contentDescription = "Play/Pause",
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
                        modifier = GlanceModifier
                            .size(44.dp)
                            .clickable(actionRunCallback<PlayPauseAction>())
                    )

                    if (size.width >= 250.dp) {
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Image(
                            provider = ImageProvider(android.R.drawable.ic_media_next),
                            contentDescription = "Next",
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
                            modifier = GlanceModifier
                                .size(36.dp)
                                .clickable(actionRunCallback<SkipNextAction>())
                        )
                    }
                }
            }
        }
    }
}
