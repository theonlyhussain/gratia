package com.gratia.music.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.hypot

/**
 * Creates a blocky, 8-bit style pixelated circle shape.
 * Perfect for stylized avatars and artist images to give them a unique, modern-retro feel.
 */
class PixelatedCircleShape(private val gridSize: Int = 24) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val cellWidth = size.width / gridSize
        val cellHeight = size.height / gridSize
        
        val radius = minOf(size.width, size.height) / 2f
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        for (x in 0 until gridSize) {
            for (y in 0 until gridSize) {
                val cellCenterX = (x * cellWidth) + (cellWidth / 2f)
                val cellCenterY = (y * cellHeight) + (cellHeight / 2f)
                
                // If the center of this cell is within the circle, include it
                if (hypot(cellCenterX - centerX, cellCenterY - centerY) <= radius) {
                    path.addRect(
                        Rect(
                            left = x * cellWidth,
                            top = y * cellHeight,
                            right = (x + 1) * cellWidth,
                            bottom = (y + 1) * cellHeight
                        )
                    )
                }
            }
        }
        
        return Outline.Generic(path)
    }
}
