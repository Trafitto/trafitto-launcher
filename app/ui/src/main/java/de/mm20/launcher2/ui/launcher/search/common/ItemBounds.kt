package de.mm20.launcher2.ui.launcher.search.common

import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.roundToIntRect

/**
 * Window bounds of a result, computed only when asked (launch, popup, return home animation).
 * Tracking them with onGloballyPositioned cost a callback, a bounds computation and a state
 * write for every visible item on every frame while the list scrolls.
 */
class ItemBounds {
    var coordinates: LayoutCoordinates? = null

    fun get(): IntRect = coordinates?.takeIf { it.isAttached }?.boundsInWindow()?.roundToIntRect()
        ?: IntRect.Zero
}
