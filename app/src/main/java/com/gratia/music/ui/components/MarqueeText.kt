package com.gratia.music.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gratia.music.ui.theme.GratiaTheme

/**
 * Text component that automatically scrolls using a marquee effect when its
 * content overflows the available width.
 *
 * It defaults to a single line since marquee text only makes sense in a
 * single-line constrained context.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarqueeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = GratiaTheme.colors.textPrimary,
    textAlign: TextAlign? = null
) {
    Text(
        text = text,
        modifier = modifier.basicMarquee(
            iterations = Int.MAX_VALUE,
            spacing = MarqueeSpacing(32.dp),
            initialDelayMillis = 1500
        ),
        color = color,
        style = style,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Clip // basicMarquee handles the overflow, clipping prevents ellipsis
    )
}
