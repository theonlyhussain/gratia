package com.gratia.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

object BounceIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return BounceIndicationNode(interactionSource)
    }

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = -1
}

private class BounceIndicationNode(
    private val interactionSource: InteractionSource
) : Modifier.Node(), DrawModifierNode {
    private val scaleAnim = Animatable(1f)

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        scaleAnim.animateTo(0.97f, tween(100))
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        scaleAnim.animateTo(1f, tween(200))
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        scale(scaleAnim.value, scaleAnim.value) {
            this@draw.drawContent()
        }
    }
}

/**
 * Adds an Apple-style press feedback animation without causing recomposition jank in lazy lists.
 */
fun Modifier.bounceClick(
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    this.clickable(
        interactionSource = interactionSource,
        indication = BounceIndication,
        onClick = onClick
    )
}
