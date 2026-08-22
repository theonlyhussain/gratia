package com.gratia.music.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.ui.Screen
import com.gratia.music.ui.theme.GratiaTheme

@Composable
fun GratiaNavigationBar(
    items: List<Screen>,
    selectedIndex: Int,
    onItemSelected: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    
    // Dynamic Pill Container
    Box(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .fillMaxWidth()
            .height(64.dp)
            .background(GratiaTheme.colors.surface, CircleShape)
            .border(1.dp, GratiaTheme.colors.textSecondary.copy(alpha = 0.15f), CircleShape)
    ) {
        // Sliding Background Pill removed in favor of inline animated pill

            // The Icons and Text
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, screen ->
                    val isSelected = index == selectedIndex
                    
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.85f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = 0.5f,
                            stiffness = 600f
                        ),
                        label = "navScale"
                    )

                    val weight by animateFloatAsState(
                        targetValue = if (isSelected) 1.5f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = 0.7f,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "navWeight"
                    )

                    val tint by animateColorAsState(
                        targetValue = if (isSelected) GratiaTheme.colors.accent else GratiaTheme.colors.textPrimary,
                        animationSpec = tween(300),
                        label = "tint"
                    )

                    // Indicator background modifier
                    val indicatorModifier = if (isSelected) {
                        Modifier
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(GratiaTheme.colors.surface)
                            .border(1.dp, GratiaTheme.colors.textSecondary.copy(alpha = 0.1f), CircleShape)
                    } else {
                        Modifier.padding(8.dp)
                    }

                    Row(
                        modifier = Modifier
                            .weight(weight)
                            .fillMaxHeight()
                            .scale(scale)
                            .then(indicatorModifier)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onItemSelected(screen) }
                            ),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected) screen.selectedIcon else screen.icon,
                            contentDescription = screen.label,
                            modifier = Modifier.size(24.dp),
                            tint = tint
                        )
                        
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn(tween(200)) + expandHorizontally(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)),
                            exit = fadeOut(tween(200)) + shrinkHorizontally(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
                        ) {
                            Row {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = screen.label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tint,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
