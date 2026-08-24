package com.gratia.music.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A standard edit affordance (pencil toggle) used across Gratia for editable surfaces.
 */
@Composable
fun GratiaEditAffordance(
    isEditing: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier
            .padding(8.dp)
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.3f))
    ) {
        AnimatedContent(
            targetState = isEditing,
            label = "EditIcon",
            transitionSpec = {
                (scaleIn(initialScale = 0.5f, animationSpec = spring(stiffness = 300f)) +
                        fadeIn(animationSpec = tween(200))).togetherWith(
                    scaleOut(targetScale = 0.5f, animationSpec = spring(stiffness = 300f)) +
                            fadeOut(animationSpec = tween(200))
                )
            }
        ) { editing ->
            if (editing) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close Edit",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit Profile",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
