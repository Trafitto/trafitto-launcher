package de.mm20.launcher2.ui.launcher.scaffold.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.SearchService
import de.mm20.launcher2.search.firstLetter
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.searchable.VisibilityLevel
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.ItemBounds
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class LetterGroup(val letter: String, val apps: List<Application>)

internal class HomeLettersVM : ViewModel(), KoinComponent {
    private val searchService: SearchService by inject()
    private val searchableRepository: SavableSearchableRepository by inject()

    /**
     * Visible apps of the personal profile by first letter, A-Z then "#". Updated only when the
     * apps change.
     */
    val groups = combine(
        searchService.getAllApps(),
        searchableRepository.getKeys(
            maxVisibility = VisibilityLevel.SearchOnly,
            includeTypes = listOf("app"),
        ),
    ) { all, hidden ->
        val hiddenKeys = hidden.toSet()
        all.standardProfileApps
            .filter { it.key !in hiddenKeys }
            .groupBy { firstLetter(it.labelOverride ?: it.label) }
            .map { (letter, apps) -> LetterGroup(letter, apps) }
            .sortedWith(compareBy({ it.letter == "#" }, { it.letter }))
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

/**
 * Letters on the edge of the home screen: slide along them to see the apps starting with that
 * letter, tap one to open it. Nothing runs at rest: the wave of the letters under the finger is
 * computed at draw time, and only the panel recomposes, when the letter changes.
 */
@Composable
internal fun HomeLetters(onLeft: Boolean, modifier: Modifier = Modifier) {
    val viewModel: HomeLettersVM = viewModel()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    if (groups.isEmpty()) return

    // Letter whose apps are shown, -1 for none. It stays open after lifting the finger.
    // Read only by the panel and at draw time, so a letter change doesn't recompose the strip.
    val selectedState = remember { mutableIntStateOf(-1) }
    var selected by selectedState
    // Finger on the strip, -1 when not touching: read only at draw and placement time
    val fingerY = remember { mutableFloatStateOf(-1f) }
    val stripHeight = remember { mutableIntStateOf(0) }
    val boxHeight = remember { mutableIntStateOf(0) }
    val panelHeight = remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current
    val textColor = homeTextColor().copy(alpha = 0.8f)
    val selectedColor = MaterialTheme.colorScheme.primary
    val letterStyle = MaterialTheme.typography.labelLarge

    val open by remember { derivedStateOf { selectedState.intValue >= 0 } }
    BackHandler(enabled = open) { selected = -1 }

    Box(modifier.onSizeChanged { boxHeight.intValue = it.height }) {
        if (open) {
            // A tap anywhere else closes the panel
            Box(Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { selected = -1 } })
        }

        Column(
            modifier = Modifier
                .align(if (onLeft) Alignment.CenterStart else Alignment.CenterEnd)
                .fillMaxHeight(0.75f)
                .width(40.dp)
                .onSizeChanged { stripHeight.intValue = it.height }
                .pointerInput(groups, haptic) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun update(y: Float) {
                            fingerY.floatValue = y
                            val i = (y / size.height * groups.size).toInt()
                                .coerceIn(0, groups.lastIndex)
                            if (i != selected) {
                                selected = i
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                        }
                        // Consumed: the home swipes don't start from the strip
                        down.consume()
                        update(down.position.y)
                        while (true) {
                            val change = awaitPointerEvent().changes
                                .firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            update(change.position.y)
                        }
                        fingerY.floatValue = -1f
                    }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            groups.forEachIndexed { i, group ->
                BasicText(
                    text = group.letter,
                    style = letterStyle,
                    color = { if (i == selectedState.intValue) selectedColor else textColor },
                    modifier = Modifier.graphicsLayer {
                        val y = fingerY.floatValue
                        if (y < 0f) return@graphicsLayer
                        // Letters near the finger grow and move away from the edge.
                        // Scale in steps of 0.1: text is rasterized for every size it is drawn
                        // at, a continuous scale uploaded new glyph textures on every frame
                        val h = stripHeight.intValue.toFloat() / groups.size
                        val k = (1f - abs((i + 0.5f) * h - y) / (h * 3f)).coerceAtLeast(0f)
                        scaleX = 1f + (7f * k).roundToInt() / 10f
                        scaleY = scaleX
                        translationX = (if (onLeft) 1f else -1f) * k * 28.dp.toPx()
                    },
                )
            }
        }

        LetterPanel(
            group = { groups.getOrNull(selectedState.intValue) },
            onLeft = onLeft,
            offsetY = {
                val h = stripHeight.intValue.toFloat() / groups.size
                val y = (selectedState.intValue + 0.5f) * h - stripHeight.intValue / 2f
                val max = ((boxHeight.intValue - panelHeight.intValue) / 2f).coerceAtLeast(0f)
                y.coerceIn(-max, max).roundToInt()
            },
            onSizeChanged = { panelHeight.intValue = it },
            onLaunched = { selected = -1 },
        )
    }
}

/**
 * The apps of the selected letter, next to the strip. Its own scope: changing letter recomposes
 * only this panel.
 */
@Composable
private fun BoxScope.LetterPanel(
    group: () -> LetterGroup?,
    onLeft: Boolean,
    offsetY: () -> Int,
    onSizeChanged: (Int) -> Unit,
    onLaunched: () -> Unit,
) {
    val group = group() ?: return
    Surface(
        modifier = Modifier
            .align(if (onLeft) Alignment.CenterStart else Alignment.CenterEnd)
            .padding(horizontal = 64.dp)
            .widthIn(min = 200.dp, max = 280.dp)
            .onSizeChanged { onSizeChanged(it.height) }
            // Centered on the selected letter, kept inside the home screen
            .offset { IntOffset(0, offsetY()) },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            for (app in group.apps) {
                key(app.key) { LetterApp(app, onLaunched = onLaunched) }
            }
        }
    }
}

@Composable
private fun LetterApp(app: Application, onLaunched: () -> Unit) {
    val viewModel: SearchableItemVM = listItemViewModel(key = "home-letter-${app.key}")
    val iconSizePx = 36.dp.toPixels()
    LaunchedEffect(app, iconSizePx) {
        viewModel.init(app, iconSizePx.toInt())
    }
    val icon by viewModel.icon.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val bounds = remember { ItemBounds() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (viewModel.launch(context, bounds.get())) onLaunched() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ShapedLauncherIcon(
            modifier = Modifier.onPlaced { bounds.coordinates = it },
            size = 36.dp,
            icon = { icon },
        )
        Text(
            text = app.labelOverride ?: app.label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
