package de.mm20.launcher2.ui.launcher.widgets.calendar

import android.content.Intent
import android.provider.CalendarContract
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.calendar.CalendarRepository
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

// ponytail: "infinite" weeks = a big pager starting in the middle, ~10 years each way
private const val WeekPages = 1000
private const val CurrentWeekPage = WeekPages / 2

/**
 * Full screen week agenda: swipe the day strip to change week, scroll the days vertically.
 * @param active true while the page is shown; going active jumps back to today and reloads events.
 */
@Composable
fun WeekCalendar(active: Boolean, modifier: Modifier = Modifier) {
    val repository: CalendarRepository = koinInject()
    val permissionsManager: PermissionsManager = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val locale = Locale.getDefault()

    val today = remember(active) { LocalDate.now() }
    val thisWeek = today.with(TemporalAdjusters.previousOrSame(WeekFields.of(locale).firstDayOfWeek))

    val weekPager = rememberPagerState(initialPage = CurrentWeekPage) { WeekPages }
    val listState = rememberLazyListState()
    fun weekOf(page: Int) = thisWeek.plusWeeks((page - CurrentWeekPage).toLong())
    val weekStart = weekOf(weekPager.currentPage)
    val days = List(7) { weekStart.plusDays(it.toLong()) }

    val hasPermission by remember { permissionsManager.hasPermission(PermissionGroup.Calendar) }
        .collectAsState(null)
    val events by remember(weekStart, active) {
        repository.findMany(
            from = weekStart.atStartOfDay(zone).toInstant().toEpochMilli(),
            to = weekStart.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }.collectAsState(emptyList<CalendarEvent>())

    LaunchedEffect(active) {
        if (active) weekPager.scrollToPage(CurrentWeekPage)
    }
    LaunchedEffect(weekStart) {
        listState.scrollToItem(days.indexOf(today).coerceAtLeast(0))
    }

    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = { scope.launch { weekPager.animateScrollToPage(weekPager.currentPage - 1) } }) {
                Icon(painterResource(R.drawable.chevron_backward_24px), null)
            }
            Text(
                text = DateTimeFormatter.ofPattern("LLLL yyyy", locale)
                    .format(weekStart.plusDays(3))
                    .replaceFirstChar { it.titlecase(locale) },
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { scope.launch { weekPager.animateScrollToPage(CurrentWeekPage) } }
                    .padding(vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { scope.launch { weekPager.animateScrollToPage(weekPager.currentPage + 1) } }) {
                Icon(painterResource(R.drawable.chevron_forward_24px), null)
            }
            IconButton(onClick = {
                context.tryStartActivity(
                    Intent(Intent.ACTION_EDIT)
                        .setData(CalendarContract.Events.CONTENT_URI)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) {
                Icon(
                    painterResource(R.drawable.add_24px),
                    stringResource(R.string.calendar_widget_create_event)
                )
            }
            IconButton(onClick = {
                context.tryStartActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setData(
                            CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
                                .appendPath(System.currentTimeMillis().toString()).build()
                        )
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) {
                Icon(
                    painterResource(R.drawable.open_in_new_24px),
                    stringResource(R.string.calendar_widget_open_calendar)
                )
            }
        }

        // Day strip: swiping here changes week, swiping anywhere else moves between launcher pages
        HorizontalPager(weekPager, Modifier.fillMaxWidth()) { page ->
            val start = weekOf(page)
            Row(Modifier.padding(horizontal = 8.dp)) {
                for (i in 0 until 7) {
                    val day = start.plusDays(i.toLong())
                    val isToday = day == today
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { scope.launch { listState.animateScrollToItem(i) } }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(36.dp)
                                .background(
                                    if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                day.dayOfMonth.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider()

        if (hasPermission == false) {
            MissingPermissionBanner(
                modifier = Modifier.padding(16.dp),
                text = stringResource(R.string.missing_permission_calendar_widget),
                onClick = {
                    permissionsManager.requestPermission(context as AppCompatActivity, PermissionGroup.Calendar)
                }
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            items(days) { day ->
                val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val dayEvents = events.filter {
                    val s = it.startTime ?: it.endTime
                    s < end && (it.endTime > start || s >= start)
                }.sortedBy { it.startTime ?: it.endTime }

                Column(Modifier.padding(vertical = 10.dp)) {
                    Text(
                        DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(day)
                            .replaceFirstChar { it.titlecase(locale) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (day == today) FontWeight.Bold else null,
                        color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    for (event in dayEvents) {
                        EventRow(event)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: CalendarEvent) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    fun time(millis: Long) = timeFormat.format(Instant.ofEpochMilli(millis).atZone(zone))

    val time = when {
        event.allDay -> stringResource(R.string.calendar_event_allday)
        event.startTime == null -> time(event.endTime)
        else -> "${time(event.startTime!!)} – ${time(event.endTime)}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { event.launch(context, null) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 36.dp)
                .background(event.color?.takeIf { it != 0 }?.let { Color(it) } ?: MaterialTheme.colorScheme.primary, CircleShape)
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                event.labelOverride ?: event.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(time, event.location?.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
