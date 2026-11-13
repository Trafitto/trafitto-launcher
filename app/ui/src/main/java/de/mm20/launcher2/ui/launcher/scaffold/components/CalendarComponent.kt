package de.mm20.launcher2.ui.launcher.scaffold.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.mm20.launcher2.ui.launcher.scaffold.LauncherScaffoldState
import de.mm20.launcher2.ui.launcher.widgets.calendar.WeekCalendar

/**
 * Full screen week calendar, to the left of the home screen.
 */
internal object CalendarComponent : ScaffoldComponent() {

    override val showSearchBar: Boolean = false
    override val survivesPause: Boolean = true

    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState
    ) {
        Surface(modifier, color = MaterialTheme.colorScheme.surface) {
            WeekCalendar(isActive, Modifier.padding(insets))
        }
    }
}
