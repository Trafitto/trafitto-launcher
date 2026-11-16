package de.mm20.launcher2.ui.settings.battery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Serializable
data object BatterySettingsRoute : NavKey

@Composable
fun BatterySettingsScreen() {
    val uiSettings: UiSettings = koinInject()
    val refreshRate by uiSettings.refreshRate.collectAsState(0)
    val opaqueSurfaces by uiSettings.opaqueSurfaces.collectAsState(false)
    val fastAnimations by uiSettings.fastAnimations.collectAsState(false)

    // Refresh rates of this screen at its current resolution: 60, 90, 120, 144…
    val display = LocalView.current.display
    val rates = remember(display) {
        val current = display?.mode ?: return@remember emptyList()
        display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .map { it.refreshRate.roundToInt() }
            .distinct()
            .sorted()
    }

    PreferenceScreen(title = stringResource(R.string.preference_screen_battery)) {
        item {
            PreferenceCategory(stringResource(R.string.preference_refresh_rate_category)) {
                Preference(
                    title = stringResource(R.string.preference_refresh_rate_impact_title),
                    summary = stringResource(R.string.preference_refresh_rate_impact),
                    onClick = {},
                )
                ListPreference(
                    title = stringResource(R.string.preference_refresh_rate),
                    items = listOf(stringResource(R.string.preference_refresh_rate_system) to 0) +
                            rates.map { rate -> refreshRateLabel(rate) to rate },
                    value = refreshRate,
                    onValueChanged = { uiSettings.setRefreshRate(it) },
                )
            }
        }
        item {
            PreferenceCategory(stringResource(R.string.preference_category_graphics)) {
                SwitchPreference(
                    title = stringResource(R.string.preference_opaque_surfaces),
                    summary = stringResource(R.string.preference_opaque_surfaces_summary),
                    value = opaqueSurfaces,
                    onValueChanged = { uiSettings.setOpaqueSurfaces(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_fast_animations),
                    summary = stringResource(R.string.preference_fast_animations_summary),
                    value = fastAnimations,
                    onValueChanged = { uiSettings.setFastAnimations(it) },
                )
            }
        }
    }
}

/**
 * "60 Hz · fewest frames", "90 Hz · +50% frames": frames drawn during animations compared to 60 Hz
 */
@Composable
private fun refreshRateLabel(rate: Int): String {
    val extra = ((rate / 60f - 1f) * 100).roundToInt()
    return if (extra <= 0) stringResource(R.string.preference_refresh_rate_lowest, rate)
    else stringResource(R.string.preference_refresh_rate_more_frames, rate, extra)
}
