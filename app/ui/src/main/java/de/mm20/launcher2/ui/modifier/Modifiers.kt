package de.mm20.launcher2.ui.modifier

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

@Stable
fun Modifier.scale(
    scaleX: Float, scaleY: Float, transformOrigin: TransformOrigin
) = if (scaleX != 1.0f || scaleY != 1.0f) {
    graphicsLayer(
        scaleX = scaleX,
        scaleY = scaleY,
        transformOrigin = transformOrigin
    )
} else {
    this
}

fun Modifier.scale(
    scale: Float,
    transformOrigin: TransformOrigin
) = scale(scale, scale, transformOrigin)

/**
 * Shrinks the content a little while it is pressed and springs it back on release.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.9f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
