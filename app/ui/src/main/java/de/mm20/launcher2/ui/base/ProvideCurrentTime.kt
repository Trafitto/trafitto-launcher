package de.mm20.launcher2.ui.base

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import de.mm20.launcher2.preferences.ClockWidgetStyle
import de.mm20.launcher2.preferences.ui.ClockWidgetSettings
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import org.koin.compose.koinInject


/**
 * Provide the current time (in millis) to the LocalTime composition local.
 * The time is updated every second when the clock widget shows seconds, otherwise every minute:
 * every reader recomposes on each update, the whole clock widget included.
 */
@Composable
fun ProvideCurrentTime(content: @Composable () -> Unit) {

    val lifecycleOwner = LocalLifecycleOwner.current
    val clockSettings: ClockWidgetSettings = koinInject()
    // ponytail: only the clock widget needs seconds (its seconds, or the blinking colon of Segment)
    val everySecond by remember {
        combine(clockSettings.showSeconds, clockSettings.clockStyle) { seconds, style ->
            seconds || style is ClockWidgetStyle.Segment
        }
    }.collectAsState(true)

    var time by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(everySecond) {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                val dateTime = Instant.now().atZone(ZoneId.systemDefault())

                time = dateTime.toEpochSecond() * 1000

                val millis = dateTime.nano / 1000000L
                val next = if (everySecond) 1000L - millis
                else (60 - dateTime.second) * 1000L - millis

                handler.postDelayed(this, next)
            }

        }
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            handler.post(runnable)
            try {
                awaitCancellation()
            } catch (e: CancellationException) {
                handler.removeCallbacks(runnable)
            }
        }
    }

    CompositionLocalProvider(
        LocalTime provides time,
        content = content
    )
}

val LocalTime = compositionLocalOf { System.currentTimeMillis() }

/**
 * True when the content is laid out but can't be seen, e.g. the home page under an open page.
 * Pauses [rememberAnimationTicker]; geometry based visibility misses that case.
 */
val LocalAnimationsPaused = compositionLocalOf { false }

/**
 * Wall clock time (millis), refreshed ~30 times per second while [running]. A cheap ticker for
 * decorative animations: an infinite transition asks for every frame (90 fps on a 90 Hz screen)
 * for as long as it is composed, even when it can't be seen.
 */
@Composable
fun rememberAnimationTicker(running: Boolean, stepMillis: Long = 33L): LongState {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val active = running && !LocalAnimationsPaused.current
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            // Waits for a frame: no updates while the window isn't drawn (e.g. launcher in background)
            withInfiniteAnimationFrameMillis { }
            now.longValue = System.currentTimeMillis()
            delay(stepMillis)
        }
    }
    return now
}
