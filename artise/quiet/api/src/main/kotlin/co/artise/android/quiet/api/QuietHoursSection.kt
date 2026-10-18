/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.quiet.api

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import java.util.Locale

/** The windows offered: common nights, so nobody has to type times. */
private val WINDOWS = listOf(20 * 60 to 8 * 60, 21 * 60 to 8 * 60, 22 * 60 to 7 * 60, 23 * 60 to 7 * 60)

/** "Silenciar fuera de horario" in Ajustes → Notificaciones, for the workspace on screen. */
@Composable
fun ColumnScope.QuietHoursSection(state: QuietHoursState, modifier: Modifier = Modifier) {
    val settings = state.settings
    ListItem(
        modifier = modifier,
        supportingContent = { Text(stringResource(R.string.artise_quiet_description)) },
        trailingContent = ListItemContent.Switch(checked = settings.enabled),
        onClick = { state.eventSink(QuietHoursEvent.Change(settings.copy(enabled = !settings.enabled))) },
    ) {
        Text(stringResource(R.string.artise_quiet_title))
    }
    if (!settings.enabled) return
    ListItem(
        supportingContent = { Text(windowText(settings.startMinute, settings.endMinute)) },
        onClick = { state.eventSink(QuietHoursEvent.OpenPicker) },
    ) {
        Text(stringResource(R.string.artise_quiet_hours))
    }
    ListItem(
        trailingContent = ListItemContent.Switch(checked = settings.wholeWeekend),
        onClick = { state.eventSink(QuietHoursEvent.Change(settings.copy(wholeWeekend = !settings.wholeWeekend))) },
    ) {
        Text(stringResource(R.string.artise_quiet_weekend))
    }
    ListItem(
        trailingContent = ListItemContent.Switch(checked = settings.allowMentions),
        onClick = { state.eventSink(QuietHoursEvent.Change(settings.copy(allowMentions = !settings.allowMentions))) },
    ) {
        Text(stringResource(R.string.artise_quiet_mentions))
    }
    if (state.isPickerVisible) WindowPicker(state)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WindowPicker(state: QuietHoursState) {
    ModalBottomSheet(onDismissRequest = { state.eventSink(QuietHoursEvent.ClosePicker) }, scrollable = false) {
        WINDOWS.forEach { (start, end) ->
            val selected = state.settings.startMinute == start && state.settings.endMinute == end
            ListItem(
                leadingContent = ListItemContent.RadioButton(selected = selected),
                onClick = {
                    state.eventSink(QuietHoursEvent.Change(state.settings.copy(startMinute = start, endMinute = end)))
                    state.eventSink(QuietHoursEvent.ClosePicker)
                },
            ) {
                Text(windowText(start, end))
            }
        }
    }
}

@Composable
private fun windowText(start: Int, end: Int): String = stringResource(R.string.artise_quiet_window, clock(start), clock(end))

private fun clock(minute: Int) = String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)

@PreviewsDayNight
@Composable
internal fun QuietHoursSectionPreview() = ElementPreview {
    androidx.compose.foundation.layout.Column {
        QuietHoursSection(QuietHoursState(QuietHoursSettings(enabled = true), isPickerVisible = false, eventSink = {}))
    }
}
