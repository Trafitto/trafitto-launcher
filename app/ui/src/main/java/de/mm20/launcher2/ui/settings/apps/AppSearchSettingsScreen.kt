package de.mm20.launcher2.ui.settings.apps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.settings.icons.IconsSettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object AppSearchSettingsRoute : NavKey

@Composable
fun AppSearchSettingsScreen() {

    val viewModel = viewModel<AppSearchSettingsScreenVM>()

    val allApps by viewModel.allApps.collectAsStateWithLifecycle()
    val allAppsByTag by viewModel.allAppsByTag.collectAsStateWithLifecycle()
    val showAppDetails by viewModel.showAppDetails.collectAsStateWithLifecycle()
    val backStack = LocalBackStack.current

    PreferenceScreen(
        title = stringResource(R.string.preference_search_apps)
    ) {
        item {
            PreferenceCategory {
                SwitchPreference(
                    icon = R.drawable.apps_24px,
                    title = stringResource(R.string.preference_search_apps),
                    summary = stringResource(R.string.preference_search_apps_summary),
                    value = allApps == true,
                    onValueChanged = {
                        viewModel.setAllApps(it)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_search_apps_by_tag),
                    summary = stringResource(R.string.preference_search_apps_by_tag_summary),
                    value = allAppsByTag == true,
                    onValueChanged = {
                        viewModel.setAllAppsByTag(it)
                    },
                    enabled = allApps == true,
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_search_apps_show_details),
                    summary = stringResource(R.string.preference_search_apps_show_details_summary),
                    value = showAppDetails == true,
                    onValueChanged = {
                        viewModel.setHideAppDetails(it)
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                // The grid and list look is set in one place, shared with the rest of the search
                Preference(
                    icon = R.drawable.apps_24px,
                    title = stringResource(R.string.preference_screen_icons),
                    summary = stringResource(R.string.preference_screen_icons_summary),
                    onClick = { backStack.add(IconsSettingsRoute) },
                )
            }
        }
    }
}