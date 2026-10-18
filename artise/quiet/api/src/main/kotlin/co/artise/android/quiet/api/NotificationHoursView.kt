/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.quiet.api

import android.content.Context
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.libraries.designsystem.components.dialogs.ListDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferenceDivider
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListSectionHeader
import io.element.android.libraries.designsystem.theme.components.ListSupportingText
import io.element.android.libraries.designsystem.theme.components.Text
import kotlinx.collections.immutable.persistentMapOf
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

/** Settings → Notificaciones → "Horario de notificaciones", for the workspace on screen. The same order as iOS. */
@Composable
fun NotificationHoursView(state: NotificationHoursState, onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    val settings = state.settings
    PreferencePage(title = stringResource(R.string.screen_artise_notification_hours_title), onBackClick = onBackClick, modifier = modifier) {
        ListItem(
            supportingContent = { Text(stringResource(R.string.screen_artise_notification_hours_description)) },
            trailingContent = ListItemContent.Switch(checked = settings.enabled),
            onClick = { state.eventSink(NotificationHoursEvent.SetEnabled(!settings.enabled)) },
        ) {
            Text(stringResource(R.string.screen_artise_notification_hours_title))
        }
        if (!settings.enabled) return@PreferencePage
        ListSectionHeader(title = stringResource(R.string.screen_artise_notification_hours_days))
        NotificationSchedule.entries.forEach { schedule ->
            ListItem(
                leadingContent = ListItemContent.RadioButton(selected = settings.schedule == schedule),
                onClick = { state.eventSink(NotificationHoursEvent.SetSchedule(schedule)) },
            ) {
                Text(scheduleName(schedule))
            }
        }
        when (settings.schedule) {
            NotificationSchedule.WEEKDAYS -> ListSupportingText(stringResource(R.string.screen_artise_notification_hours_weekdays_footer))
            NotificationSchedule.CUSTOM -> ListSupportingText(stringResource(R.string.screen_artise_notification_hours_custom_footer))
            NotificationSchedule.EVERY_DAY -> Unit
        }
        if (settings.schedule == NotificationSchedule.CUSTOM) {
            DayOfWeek.entries.forEach { day ->
                val window = settings.days[day]
                PreferenceDivider()
                ListItem(
                    trailingContent = ListItemContent.Switch(checked = window != null),
                    onClick = { state.eventSink(NotificationHoursEvent.SetDay(day, on = window == null)) },
                ) {
                    Text(dayName(day))
                }
                if (window != null) TimeRows(state, day, window)
            }
        } else {
            ListSectionHeader(title = stringResource(R.string.screen_artise_notification_hours_hours))
            TimeRows(state, day = null, window = settings.window)
        }
        PreferenceDivider()
        ListItem(
            trailingContent = ListItemContent.Switch(checked = settings.allowMentions),
            onClick = { state.eventSink(NotificationHoursEvent.SetAllowMentions(!settings.allowMentions)) },
        ) {
            Text(stringResource(R.string.screen_artise_notification_hours_mentions))
        }
        state.picking?.let { TimePickerDialog(state, it) }
    }
}

@Composable
private fun TimeRows(state: NotificationHoursState, day: DayOfWeek?, window: HoursWindow) {
    listOf(true to window.startMinute, false to window.endMinute).forEach { (isStart, minute) ->
        ListItem(
            trailingContent = ListItemContent.Text(clockText(LocalContext.current, minute)),
            onClick = { state.eventSink(NotificationHoursEvent.Pick(HoursField(day, isStart))) },
        ) {
            Text(stringResource(if (isStart) R.string.screen_artise_notification_hours_from else R.string.screen_artise_notification_hours_until))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(state: NotificationHoursState, field: HoursField) {
    val window = field.day?.let { state.settings.days[it] } ?: state.settings.window
    val minute = if (field.isStart) window.startMinute else window.endMinute
    val context = LocalContext.current
    val pickerState = rememberTimePickerState(
        initialHour = minute / 60,
        initialMinute = minute % 60,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    ListDialog(
        title = stringResource(if (field.isStart) R.string.screen_artise_notification_hours_from else R.string.screen_artise_notification_hours_until),
        onSubmit = {
            state.eventSink(NotificationHoursEvent.SetTime(field, pickerState.hour * 60 + pickerState.minute))
            state.eventSink(NotificationHoursEvent.ClosePicker)
        },
        onDismissRequest = { state.eventSink(NotificationHoursEvent.ClosePicker) },
    ) {
        item { TimePicker(state = pickerState) }
    }
}

@Composable
internal fun scheduleName(schedule: NotificationSchedule): String = stringResource(
    when (schedule) {
        NotificationSchedule.EVERY_DAY -> R.string.screen_artise_notification_hours_every_day
        NotificationSchedule.WEEKDAYS -> R.string.screen_artise_notification_hours_weekdays
        NotificationSchedule.CUSTOM -> R.string.screen_artise_notification_hours_custom
    }
)

/** The day's name in the phone's language, starting with a capital letter ("lunes" becomes "Lunes"). */
private fun dayName(day: DayOfWeek): String {
    val locale = Locale.getDefault()
    return day.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
}

/** A time the way the phone shows it, following its 12 or 24 hour setting. */
internal fun clockText(context: Context, minute: Int): String {
    val time = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minute / 60)
        set(Calendar.MINUTE, minute % 60)
    }
    return android.text.format.DateFormat.getTimeFormat(context).format(time.time)
}

internal class NotificationHoursStateProvider : PreviewParameterProvider<NotificationHoursState> {
    private val on = NotificationHoursSettings(enabled = true)
    override val values = sequenceOf(
        aNotificationHoursState(NotificationHoursSettings()),
        aNotificationHoursState(on),
        aNotificationHoursState(on.copy(schedule = NotificationSchedule.WEEKDAYS, window = HoursWindow(9 * 60, 18 * 60))),
        aNotificationHoursState(
            on.copy(
                schedule = NotificationSchedule.CUSTOM,
                days = persistentMapOf(DayOfWeek.MONDAY to HoursWindow(9 * 60, 18 * 60), DayOfWeek.FRIDAY to HoursWindow(18 * 60, 2 * 60)),
            )
        ),
        aNotificationHoursState(on, picking = HoursField(day = null, isStart = true)),
    )
}

internal fun aNotificationHoursState(settings: NotificationHoursSettings, picking: HoursField? = null) =
    NotificationHoursState(settings = settings, picking = picking, eventSink = {})

@PreviewsDayNight
@Composable
internal fun NotificationHoursViewPreview(@PreviewParameter(NotificationHoursStateProvider::class) state: NotificationHoursState) = ElementPreview {
    NotificationHoursView(state = state, onBackClick = {})
}
