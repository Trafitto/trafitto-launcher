package de.mm20.launcher2.ui.launcher.scaffold.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.mm20.launcher2.preferences.WidgetScreenTarget
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.ui.launcher.scaffold.LauncherScaffoldState
import org.koin.compose.koinInject
import java.util.UUID

/**
 * Widget pages to the right of the home screen, as many as set in Settings > Home screen > Widgets.
 * Each page is a widget screen of its own, empty at first.
 */
internal object WidgetPagesComponent : ScaffoldComponent() {

    private var pageCount by mutableIntStateOf(1)
    private val pagerState = PagerState { pageCount }

    /**
     * Page 0 keeps the screen these widgets used before there were several pages (Widgets2;
     * Widgets1 is the stock screen with Kvaesitso's default widgets). The others get ids of their own.
     * ponytail: pages removed in the settings keep their widgets in the DB, they come back if re-added
     */
    private fun page(index: Int) = WidgetsComponent.forParent(
        if (index == 0) WidgetScreenTarget.Widgets2.id else UUID(1L, index.toLong())
    )

    private val currentPage get() = page(pagerState.currentPage)

    override val isAtTop: State<Boolean?> get() = currentPage.isAtTop
    override val isAtBottom: State<Boolean?> get() = currentPage.isAtBottom
    override val hasIme: Boolean = true // note widget

    // A hidden search bar stays hidden here too; search is a swipe up from home
    override val showSearchBar: Boolean = false

    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState
    ) {
        val uiSettings: UiSettings = koinInject()
        val count by remember { uiSettings.widgetPages }.collectAsState(pageCount)
        SideEffect { pageCount = count }

        HorizontalPager(
            state = pagerState,
            modifier = modifier,
            // Not while editing widgets (the scaffold is locked then)
            userScrollEnabled = !state.isLocked,
        ) { index ->
            // One root: the widget column and its edit top bar are two siblings
            Box(Modifier.fillMaxSize()) {
                page(index).Component(Modifier.fillMaxSize(), insets, state)
            }
        }
    }

    override fun onPreActivate(state: LauncherScaffoldState) {
        super.onPreActivate(state)
        currentPage.onPreActivate(state)
    }

    override suspend fun onActivate(state: LauncherScaffoldState) {
        super.onActivate(state)
        currentPage.onActivate(state)
    }

    override fun onPreDismiss(state: LauncherScaffoldState) {
        super.onPreDismiss(state)
        currentPage.onPreDismiss(state)
    }

    override suspend fun onDismiss(state: LauncherScaffoldState) {
        super.onDismiss(state)
        currentPage.onDismiss(state)
        pagerState.scrollToPage(0)
    }
}
