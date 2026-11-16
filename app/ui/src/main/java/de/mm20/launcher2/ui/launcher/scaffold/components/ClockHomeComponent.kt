package de.mm20.launcher2.ui.launcher.scaffold.components

import android.app.PendingIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.searchable.VisibilityLevel
import kotlinx.coroutines.flow.combine
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
 * The first 6 pinned apps, shortcuts and categories (as folders). "+ Add app" or a long press
 * opens [EditHomeAppsSheet],
 * the look is set in Settings > Home screen.
 */
@Composable
private fun HomeFavorites(settings: HomeAppsSettings) {
    val favoritesService: FavoritesService = koinInject()
    val customAttributesRepository: CustomAttributesRepository = koinInject()
    val apps by remember {
        favoritesService.getFavorites(
            // Shortcuts too: "Add to home screen" from a browser pins them here.
            // A pinned category is a folder
            includeTypes = listOf("app", "shortcut", "legacyshortcut", "tag"),
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
    val customAttributesRepository: CustomAttributesRepository = koinInject()
    val tags by remember { customAttributesRepository.getAllTags() }.collectAsState(emptyList())
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
                        leadingIcon = if (app !is Tag) null else {
                            {
                                Icon(
                                    painterResource(R.drawable.folder_24px), null,
                                    Modifier.size(InputChipDefaults.IconSize),
                                )
                            }
                        },
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
                val pinnedTags = apps.mapNotNull { (it as? Tag)?.tag }
                val freeTags = tags.filter { it !in pinnedTags }
                if (freeTags.isNotEmpty()) {
                    Text(
                        stringResource(R.string.home_apps_folders),
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    // One scrollable row, so search results stay visible above the keyboard
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (tag in freeTags) {
                            AssistChip(
                                onClick = { favoritesService.pinItem(Tag(tag)) },
                                label = { Text(tag) },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.folder_24px), null,
                                        Modifier.size(AssistChipDefaults.IconSize),
                                    )
                                },
                            )
                        }
                    }
                }
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
    // A pinned category: a tap opens its apps next to it
    val folder = item as? Tag
    var showFolder by remember { mutableStateOf(false) }
    val folderItems by rememberFolderItems(folder)

    val iconView = @Composable {
        if (settings.showIcons) {
            val modifier = if (settings.monochromeIcons) Modifier.grayscale() else Modifier
            val shape = settings.iconShape?.let { getShape(it) } ?: LocalIconShape.current
            if (folder != null) {
                FolderIcon(folderItems, iconSize, shape, modifier)
            } else {
                ShapedLauncherIcon(
                    modifier = modifier,
                    size = iconSize,
                    icon = { icon },
                    shape = shape,
                )
            }
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
    // The folder popup is placed next to this Box. Not a child of the Row: it would take a spacing
    Box {
        Row(
            modifier = Modifier
                .onGloballyPositioned { bounds = it.boundsInWindow().roundToIntRect() }
                .then(
                    if (!settings.swipeForShortcuts || folder != null) Modifier
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
                    onClick = {
                        if (folder != null) showFolder = true
                        else if (!viewModel.launch(context, bounds)) showPopup = true
                    },
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
        if (showFolder) {
            FolderPopup(folderItems, settings.position, onDismiss = { showFolder = false })
        }
    }

    if (showPopup) {
        ItemPopup(origin = bounds, searchable = item, onDismissRequest = { showPopup = false })
    }
}

/** The visible items of a category, by name. Nothing for an item that isn't a category */
@Composable
private fun rememberFolderItems(folder: Tag?): State<List<SavableSearchable>> {
    val customAttributesRepository: CustomAttributesRepository = koinInject()
    val searchableRepository: SavableSearchableRepository = koinInject()
    return remember(folder) {
        if (folder == null) flowOf(emptyList())
        else combine(
            customAttributesRepository.getItemsForTag(folder.tag)
                .withCustomLabels(customAttributesRepository),
            searchableRepository.getKeys(maxVisibility = VisibilityLevel.SearchOnly),
        ) { items, hidden ->
            val hiddenKeys = hidden.toSet()
            items.filter { it.key !in hiddenKeys }.sorted()
        }
    }.collectAsState(emptyList())
}

/** The first 4 icons of the folder in a 2x2 grid, on the icon shape */
@Composable
private fun FolderIcon(items: List<SavableSearchable>, size: Dp, shape: Shape, modifier: Modifier) {
    Box(
        modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f), shape)
            .padding(size * 0.12f)
    ) {
        val corners = listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd)
        items.take(4).forEachIndexed { i, item ->
            key(item.key) {
                FolderIconItem(item, size * 0.34f, Modifier.align(corners[i]))
            }
        }
    }
}

@Composable
private fun FolderIconItem(item: SavableSearchable, size: Dp, modifier: Modifier) {
    val viewModel: SearchableItemVM = listItemViewModel(key = "home-folder-${item.key}")
    val sizePx = size.toPixels()
    LaunchedEffect(item, sizePx) {
        viewModel.init(item, sizePx.toInt())
    }
    val icon by viewModel.icon.collectAsStateWithLifecycle()
    ShapedLauncherIcon(modifier = modifier, size = size, icon = { icon })
}

/** The apps of a folder in a panel next to it; a tap outside or back closes it */
@Composable
private fun FolderPopup(
    items: List<SavableSearchable>,
    position: HomeAppsPosition,
    onDismiss: () -> Unit,
) {
    Popup(
        popupPositionProvider = remember(position) { FolderPosition(position) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val visible = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(visible, enter = fadeIn() + scaleIn(initialScale = 0.9f)) {
            AppsPanel(items, onLaunched = onDismiss, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

/**
 * Lined up with the home list; above the folder when the list is at the bottom, below it when
 * at the top, on the other side if it doesn't fit
 */
private class FolderPosition(private val position: HomeAppsPosition) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = when (position) {
            HomeAppsPosition.TopStart, HomeAppsPosition.BottomStart -> anchorBounds.left
            HomeAppsPosition.TopEnd, HomeAppsPosition.BottomEnd -> anchorBounds.right - popupContentSize.width
            else -> anchorBounds.center.x - popupContentSize.width / 2
        }
        val above = anchorBounds.top - popupContentSize.height
        val below = anchorBounds.bottom
        val atBottom = position in listOf(
            HomeAppsPosition.BottomStart, HomeAppsPosition.BottomCenter, HomeAppsPosition.BottomEnd
        )
        val y = if (atBottom && above >= 0 || !atBottom && below + popupContentSize.height > windowSize.height) above
        else below
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceAtLeast(0),
        )
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
