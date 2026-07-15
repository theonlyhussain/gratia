package com.gratia.music.ui.lyrics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.ui.theme.Inter

/**
 * Plain lyrics mode.
 * Displays raw text in a vertical scrolling layout with nice paragraph spacings.
 */
@Composable
fun PlainLyricsView(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp)
        ) {
            val paragraphs = text.split("\n\n")
            paragraphs.forEach { paragraph ->
                Text(
                    text = paragraph,
                    fontFamily = Inter,
                    fontSize = 22.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.75f),
                    lineHeight = 34.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
        }
    }
}
