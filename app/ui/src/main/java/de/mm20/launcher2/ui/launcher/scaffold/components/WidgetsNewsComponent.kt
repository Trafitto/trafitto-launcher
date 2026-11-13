package de.mm20.launcher2.ui.launcher.scaffold.components

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.preferences.WidgetScreenTarget
import de.mm20.launcher2.ui.launcher.scaffold.LauncherScaffoldState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pages to the right of the home screen: a widget screen of its own (empty at first, add app
 * widgets with the edit button), then Google News one swipe further.
 *
 * Google News is the real app, opened when the pager reaches page 1: the Google feed can't be
 * embedded in a non-system launcher, the app at least has the user's account.
 */
internal class WidgetsNewsComponent(
    private val activity: Activity,
) : ScaffoldComponent() {

    // Widgets1 is the stock widget screen (with Kvaesitso's default widgets), this one starts empty
    private val widgets = WidgetsComponent.forTarget(WidgetScreenTarget.Widgets2)

    private val pagerState = PagerState { 2 }

    override val isAtTop: State<Boolean?> get() = widgets.isAtTop
    override val isAtBottom: State<Boolean?> get() = widgets.isAtBottom
    override val hasIme: Boolean get() = widgets.hasIme

    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState
    ) {
        LaunchedEffect(pagerState.settledPage) {
            if (pagerState.settledPage == 1) {
                openGoogleNews()
                // Back from Google News lands on the widgets
                delay(500.milliseconds)
                pagerState.scrollToPage(0)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = modifier,
            // Not while editing widgets (the scaffold is locked then)
            userScrollEnabled = !state.isLocked,
        ) { page ->
            if (page == 0) {
                // One root: the widget column and its edit top bar are two siblings
                Box(Modifier.fillMaxSize()) {
                    widgets.Component(Modifier.fillMaxSize(), insets, state)
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Google News", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }

    private fun openGoogleNews() {
        val intent = activity.packageManager.getLaunchIntentForPackage(GoogleNews)
            ?: Intent(Intent.ACTION_VIEW, "https://news.google.com/".toUri())
        // The pager already slid the "Google News" page in, the app just fades in on top
        val options = ActivityOptions.makeCustomAnimation(
            activity,
            android.R.anim.fade_in,
            android.R.anim.fade_out,
        )
        activity.tryStartActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle())
    }

    override fun onPreActivate(state: LauncherScaffoldState) {
        super.onPreActivate(state)
        widgets.onPreActivate(state)
    }

    override suspend fun onActivate(state: LauncherScaffoldState) {
        super.onActivate(state)
        widgets.onActivate(state)
    }

    override fun onPreDismiss(state: LauncherScaffoldState) {
        super.onPreDismiss(state)
        widgets.onPreDismiss(state)
    }

    override suspend fun onDismiss(state: LauncherScaffoldState) {
        super.onDismiss(state)
        widgets.onDismiss(state)
        pagerState.scrollToPage(0)
    }

    companion object {
        private const val GoogleNews = "com.google.android.apps.magazines"
    }
}
