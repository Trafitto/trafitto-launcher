package de.mm20.launcher2.ui.launcher.widgets.calendar

import android.content.Intent
import android.provider.CalendarContract
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.mm20.launcher2.calendar.CalendarRepository
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

// ponytail: "infinite" agenda = a big list of days starting in the middle, ~10 years each way
private const val WeekPages = 1000
private const val CurrentWeekPage = WeekPages / 2
private const val DayCount = WeekPages * 7

// Events are loaded for a window of weeks around the visible one, moved in steps of 4 weeks
private const val LoadStepWeeks = 4

// The page leaves the composition when closed: the events of the last windows seen are kept here,
// so they show at once when it opens again, and the fresh query only changes what has changed.
// ponytail: process wide, last 6 windows, main thread only
private val eventCache = object : LinkedHashMap<LocalDate, List<CalendarEvent>>(8, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LocalDate, List<CalendarEvent>>?) = size > 6
}

/**
 * Full screen agenda: opens on today, scroll down for the next days and weeks, up for the past
 * ones. The week strip on top follows the list; swiping it jumps to another week.
 * @param active true while the page is shown; going active jumps back to today.
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
    // Item i of the list is a day, item 7 * page is the first day of that week
    fun dayAt(index: Int): LocalDate = thisWeek.plusDays((index - CurrentWeekPage * 7).toLong())
    val todayIndex = CurrentWeekPage * 7 + ChronoUnit.DAYS.between(thisWeek, today).toInt()

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = todayIndex)
    val weekPager = rememberPagerState(initialPage = CurrentWeekPage) { WeekPages }
    val firstDay by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val visibleWeek = firstDay / 7

    LaunchedEffect(active) {
        if (active) listState.scrollToItem(todayIndex)
    }
    // The strip follows the list (at once, so a settled strip always matches the list)...
    LaunchedEffect(visibleWeek) {
        if (!weekPager.isScrollInProgress && weekPager.currentPage != visibleWeek) {
            weekPager.scrollToPage(visibleWeek)
        }
    }
    // ...and a swipe on the strip moves the list to the first day of that week
    LaunchedEffect(Unit) {
        snapshotFlow { weekPager.settledPage }.collect { page ->
            if (!listState.isScrollInProgress && page != listState.firstVisibleItemIndex / 7) {
                listState.scrollToItem(page * 7)
            }
        }
    }

    val hasPermission by remember { permissionsManager.hasPermission(PermissionGroup.Calendar) }
        .collectAsState(null)
    val windowStart = dayAt((visibleWeek / LoadStepWeeks * LoadStepWeeks - LoadStepWeeks) * 7)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // produceState keeps the old events while the next window loads: no blank days while scrolling.
    // Queried again on every return to the launcher (an event added with + shows up); an equal
    // list doesn't recompose anything
    val events by produceState(eventCache[windowStart] ?: emptyList(), windowStart) {
        eventCache[windowStart]?.let { value = it }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            repository.findMany(
                from = windowStart.atStartOfDay(zone).toInstant().toEpochMilli(),
                to = windowStart.plusWeeks(3L * LoadStepWeeks).atStartOfDay(zone).toInstant().toEpochMilli(),
            ).collect {
                value = it
                eventCache[windowStart] = it
            }
        }
    }
    val eventsByDay = remember(events) { groupByDay(events, zone) }

    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = { scope.launch { listState.animateScrollToItem((visibleWeek - 1) * 7) } }) {
                Icon(painterResource(R.drawable.chevron_backward_24px), null)
            }
            Text(
                text = DateTimeFormatter.ofPattern("LLLL yyyy", locale)
                    .format(dayAt(firstDay))
                    .replaceFirstChar { it.titlecase(locale) },
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { scope.launch { listState.animateScrollToItem(todayIndex) } }
                    .padding(vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { scope.launch { listState.animateScrollToItem((visibleWeek + 1) * 7) } }) {
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
            Row(Modifier.padding(horizontal = 8.dp)) {
                for (i in 0 until 7) {
                    val index = page * 7 + i
                    val day = dayAt(index)
                    StripDay(
                        day = day,
                        locale = locale,
                        isToday = day == today,
                        isTop = index == firstDay,
                        eventColors = eventsByDay[day].orEmpty().map { eventColor(it) }.distinct().take(3),
                        onClick = { scope.launch { listState.animateScrollToItem(index) } },
                        modifier = Modifier.weight(1f),
                    )
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
            items(DayCount, key = { it }) { index ->
                val day = dayAt(index)
                AgendaDay(
                    day = day,
                    locale = locale,
                    today = today,
                    events = eventsByDay[day].orEmpty(),
                )
            }
        }
    }
}

/**
 * Events by the days they cover: one that lasts several days shows on each of them.
 */
private fun groupByDay(events: List<CalendarEvent>, zone: ZoneId): Map<LocalDate, List<CalendarEvent>> {
    val byDay = HashMap<LocalDate, MutableList<CalendarEvent>>()
    for (event in events.sortedBy { it.startTime ?: it.endTime }) {
        val start = event.startTime ?: event.endTime
        val first = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        // The end is exclusive: an event ending at midnight doesn't show on the next day
        val last = Instant.ofEpochMilli(maxOf(event.endTime - 1, start)).atZone(zone).toLocalDate()
        // ponytail: capped at 2 months, a longer event only shows on its first 2 months
        var day = first
        while (!day.isAfter(last) && day.isBefore(first.plusMonths(2))) {
            byDay.getOrPut(day) { mutableListOf() } += event
            day = day.plusDays(1)
        }
    }
    return byDay
}

@Composable
private fun eventColor(event: CalendarEvent): Color {
    // Some providers store the color without alpha
    return event.color?.takeIf { it != 0 }?.let { Color(it or 0xFF000000.toInt()) }
        ?: MaterialTheme.colorScheme.primary
}

private fun isWeekend(day: LocalDate) = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY

@Composable
private fun StripDay(
    day: LocalDate,
    locale: Locale,
    isToday: Boolean,
    isTop: Boolean,
    eventColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
            style = MaterialTheme.typography.labelMedium,
            color = if (isWeekend(day)) colors.tertiary else colors.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(36.dp)
                .background(
                    when {
                        isToday -> colors.primary
                        isTop -> colors.secondaryContainer
                        else -> Color.Transparent
                    },
                    CircleShape
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    isToday -> colors.onPrimary
                    isTop -> colors.onSecondaryContainer
                    else -> colors.onSurface
                },
            )
        }
        // One dot per calendar with events that day
        Row(
            modifier = Modifier
                .padding(top = 4.dp)
                .height(6.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (color in eventColors) {
                Box(Modifier.size(6.dp).background(color, CircleShape))
            }
        }
    }
}

@Composable
private fun AgendaDay(
    day: LocalDate,
    locale: Locale,
    today: LocalDate,
    events: List<CalendarEvent>,
) {
    val colors = MaterialTheme.colorScheme
    val isToday = day == today
    Column(
        // Past days fade a bit, so today and what comes next stand out
        Modifier.alpha(if (day < today) 0.6f else 1f)
    ) {
        if (day.dayOfMonth == 1) {
            // A colored band where a new month starts
            Text(
                DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(day)
                    .replaceFirstChar { it.titlecase(locale) },
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(colors.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onPrimaryContainer,
            )
        }
        Row(Modifier.padding(vertical = 8.dp)) {
            // Date badge: today filled with the primary color, weekends in the tertiary one
            Column(
                modifier = Modifier
                    .width(52.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        when {
                            isToday -> colors.primary
                            isWeekend(day) -> colors.tertiaryContainer
                            else -> colors.surfaceContainerHigh
                        }
                    )
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val content = when {
                    isToday -> colors.onPrimary
                    isWeekend(day) -> colors.onTertiaryContainer
                    else -> colors.onSurface
                }
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = content,
                )
                Text(
                    day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = content,
                )
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                if (isToday || day == today.plusDays(1)) {
                    Text(
                        stringResource(if (isToday) R.string.date_today else R.string.date_tomorrow),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                if (events.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = colors.outlineVariant)
                }
                for (event in events) {
                    EventRow(event)
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
    val color = eventColor(event)
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(MaterialTheme.shapes.medium)
            // Tinted with the calendar color: light enough for the text to stay readable
            .background(color.copy(alpha = if (event.allDay) 0.36f else 0.26f).compositeOver(colors.surfaceContainer))
            .clickable { event.launch(context, null) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 36.dp)
                .background(color, CircleShape)
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                event.labelOverride ?: event.label,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(time, event.location?.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
