package de.mm20.launcher2.ui.launcher.scaffold.components

import android.app.PendingIntent
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.data.customattrs.utils.withCustomLabels
import de.mm20.launcher2.ktx.sendWithBackgroundPermission
import de.mm20.launcher2.preferences.HomeAppsMode
import de.mm20.launcher2.preferences.HomeAppsPosition
import de.mm20.launcher2.preferences.HomeAppsSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.PinnedLevel
import de.mm20.launcher2.services.favorites.FavoritesService
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.LocalAnimationsPaused
import de.mm20.launcher2.ui.common.SearchablePicker
import de.mm20.launcher2.ui.common.SearchablePickerVM
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.LocalIconShape
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.getShape
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.scaffold.LauncherScaffoldState
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.common.grid.ItemPopup
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import de.mm20.launcher2.ui.launcher.widgets.clock.ClockWidget
import de.mm20.launcher2.ui.locals.LocalPreferDarkContentOverWallpaper
import de.mm20.launcher2.ui.modifier.pressScale
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject
import kotlin.math.abs

internal object ClockHomeComponent : ScaffoldComponent() {

    override val drawBackground: Boolean = false

    override var isAtTop: State<Boolean?> = mutableStateOf(true)
    override var isAtBottom: State<Boolean?> = mutableStateOf(true)

    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState,
    ) {
        val uiSettings: UiSettings = koinInject()
        val settings by remember { uiSettings.homeApps }.collectAsState(HomeAppsSettings())
        val onTop = settings.position in listOf(
            HomeAppsPosition.TopStart, HomeAppsPosition.TopCenter, HomeAppsPosition.TopEnd
        )
        // Pause the clock's animations while another page covers the home page
        val covered by remember(state) {
            derivedStateOf { state.isSettledOnSecondaryPage && state.currentProgress == 1f }
        }
        CompositionLocalProvider(LocalAnimationsPaused provides covered) {
            Column(
                modifier = modifier
                    .padding(insets)
                    .pointerInput(Unit) {},
            ) {
                val pinned = settings.mode == HomeAppsMode.Pinned
                if (onTop && pinned) HomeFavorites(settings)
                Box(Modifier.weight(1f)) {
                    ClockWidget(
                        modifier = Modifier.fillMaxSize(),
                        fillScreenHeight = true,
                    )
                    if (settings.mode == HomeAppsMode.Letters) {
                        HomeLetters(
                            onLeft = settings.position in listOf(
                                HomeAppsPosition.TopStart, HomeAppsPosition.BottomStart
                            ),
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                }
                if (!onTop && pinned) HomeFavorites(settings)
            }
        }
    }
}

private const val MaxHomeApps = 6

/**
 * The first 6 pinned apps. "+ Add app" or a long press opens [EditHomeAppsSheet],
 * the look is set in Settings > Home screen.
 */
@Composable
private fun HomeFavorites(settings: HomeAppsSettings) {
    val favoritesService: FavoritesService = koinInject()
    val customAttributesRepository: CustomAttributesRepository = koinInject()
    val apps by remember {
        favoritesService.getFavorites(
            includeTypes = listOf("app"),
            minPinnedLevel = PinnedLevel.AutomaticallySorted,
            limit = MaxHomeApps,
        ).withCustomLabels(customAttributesRepository)
    }.collectAsState(emptyList())
    var editing by remember { mutableStateOf(false) }

    val alignment = when (settings.position) {
        HomeAppsPosition.TopStart, HomeAppsPosition.BottomStart -> Alignment.Start
        HomeAppsPosition.TopCenter, HomeAppsPosition.BottomCenter -> Alignment.CenterHorizontally
        HomeAppsPosition.TopEnd, HomeAppsPosition.BottomEnd -> Alignment.End
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = alignment,
    ) {
        for (app in apps) {
            key(app.key) {
                HomeFavorite(
                    item = app,
                    settings = settings,
                    // Right aligned list: icon on the right, next to the screen edge
                    iconAfterLabel = alignment == Alignment.End,
                    onLongClick = { editing = true },
                )
            }
        }
        if (apps.size < MaxHomeApps) {
            Text(
                text = "+ " + stringResource(R.string.home_apps_add),
                modifier = Modifier
                    .clickable { editing = true }
                    .padding(vertical = (settings.spacing / 2).dp),
                style = MaterialTheme.typography.titleMedium,
                color = homeTextColor().copy(alpha = 0.7f),
            )
        }
    }
    if (editing) {
        EditHomeAppsSheet(apps, onDismiss = { editing = false })
    }
}

@Composable
internal fun homeTextColor() =
    if (LocalPreferDarkContentOverWallpaper.current) Color.Black else Color.White

@Composable
private fun EditHomeAppsSheet(apps: List<SavableSearchable>, onDismiss: () -> Unit) {
    val favoritesService: FavoritesService = koinInject()
    // The picker's VM is activity scoped: start every time with an empty search
    val pickerVM: SearchablePickerVM = viewModel()
    LaunchedEffect(Unit) { pickerVM.onSearchQueryChanged("") }
    DismissableBottomSheet(expanded = true, onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.home_apps_title, MaxHomeApps),
                style = MaterialTheme.typography.titleLarge,
            )
            // Chips keep this short, so search results stay visible above the keyboard
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (app in apps) {
                    InputChip(
                        selected = false,
                        onClick = { favoritesService.unpinItem(app) },
                        label = { Text(app.labelOverride ?: app.label) },
                        trailingIcon = {
                            Icon(
                                painterResource(R.drawable.close_24px),
                                stringResource(R.string.menu_remove),
                                Modifier.size(InputChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }
            if (apps.size < MaxHomeApps) {
                SearchablePicker(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .weight(1f, fill = false),
                    value = null,
                    onValueChanged = { if (it is Application) favoritesService.pinItem(it) },
                )
            }
        }
    }
}

@Composable
private fun HomeFavorite(
    item: SavableSearchable,
    settings: HomeAppsSettings,
    iconAfterLabel: Boolean,
    onLongClick: () -> Unit,
) {
    val viewModel: SearchableItemVM = listItemViewModel(key = "home-favorite-${item.key}")
    val iconSize = settings.iconSize.dp
    val iconSizePx = iconSize.toPixels()
    LaunchedEffect(item, iconSizePx) {
        viewModel.init(item, iconSizePx.toInt())
    }
    val icon by viewModel.icon.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var bounds by remember { mutableStateOf(IntRect.Zero) }
    var showPopup by remember { mutableStateOf(false) }

    val iconView = @Composable {
        if (settings.showIcons) {
            ShapedLauncherIcon(
                modifier = if (settings.monochromeIcons) Modifier.grayscale() else Modifier,
                size = iconSize,
                icon = { icon },
                shape = settings.iconShape?.let { getShape(it) } ?: LocalIconShape.current,
            )
        }
    }
    // White text needs a soft shadow to stay readable on light parts of the wallpaper
    val textShadow = if (LocalPreferDarkContentOverWallpaper.current) null
    else Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 8f)

    // The newest notification with something to read, no ongoing ones (music, downloads).
    // Recomputed only when the notifications change.
    val notification by remember(settings.showNotifications) {
        if (!settings.showNotifications) flowOf(null)
        else viewModel.notifications.map { list ->
            list.filter { it.isClearable && (it.title != null || it.text != null) }
                .maxByOrNull { it.postTime }
        }
    }.collectAsStateWithLifecycle(null)

    val label = @Composable {
        Column(horizontalAlignment = if (iconAfterLabel) Alignment.End else Alignment.Start) {
            if (settings.showLabels) {
                Text(
                    text = item.labelOverride ?: item.label,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = settings.labelSize.sp,
                        fontWeight = if (settings.boldLabels) FontWeight.Bold else null,
                        shadow = textShadow,
                    ),
                    color = homeTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            notification?.let { n ->
                Text(
                    text = listOfNotNull(n.title, n.text).joinToString(": "),
                    style = MaterialTheme.typography.bodyMedium.copy(shadow = textShadow),
                    color = homeTextColor().copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .clickable {
                            // Opens the notification, or the app when it has nothing to open
                            val intent = n.contentIntent
                            val opened = intent != null && try {
                                intent.sendWithBackgroundPermission(context)
                                true
                            } catch (e: PendingIntent.CanceledException) {
                                false
                            }
                            if (!opened) viewModel.launch(context, bounds)
                        },
                )
            }
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    // Side swipe: the row follows the finger a little, past the threshold it ticks and on
    // release opens the shortcuts. Offset read at draw time.
    val swipe = remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    Row(
        modifier = Modifier
            .onGloballyPositioned { bounds = it.boundsInWindow().roundToIntRect() }
            .then(
                if (!settings.swipeForShortcuts) Modifier
                else Modifier
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            val before = swipe.floatValue
                            swipe.floatValue = before + delta
                            if (abs(before) < swipeThreshold && abs(swipe.floatValue) >= swipeThreshold) {
                                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            }
                        },
                        onDragStopped = {
                            if (abs(swipe.floatValue) >= swipeThreshold) showPopup = true
                            swipe.floatValue = 0f
                        },
                    )
                    .graphicsLayer {
                        translationX = (swipe.floatValue * 0.4f).coerceIn(-32.dp.toPx(), 32.dp.toPx())
                    }
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = { if (!viewModel.launch(context, bounds)) showPopup = true },
                onLongClick = onLongClick,
            )
            .pressScale(interactionSource, pressedScale = 0.95f)
            .padding(vertical = (settings.spacing / 2).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (iconAfterLabel) {
            label()
            iconView()
        } else {
            iconView()
            label()
        }
    }

    if (showPopup) {
        ItemPopup(origin = bounds, searchable = item, onDismissRequest = { showPopup = false })
    }
}

// ponytail: saturation 0 colour matrix on a layer, works on every API level (RenderEffect needs 31+)
private val GrayscalePaint = Paint().apply {
    colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
}

private fun Modifier.grayscale() = drawWithContent {
    drawIntoCanvas {
        it.saveLayer(Rect(Offset.Zero, size), GrayscalePaint)
        drawContent()
        it.restore()
    }
}
