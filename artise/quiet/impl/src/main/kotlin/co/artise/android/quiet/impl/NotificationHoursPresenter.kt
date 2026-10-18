/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.quiet.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import co.artise.android.quiet.api.HoursField
import co.artise.android.quiet.api.NotificationHoursEvent
import co.artise.android.quiet.api.NotificationHoursSettings
import co.artise.android.quiet.api.NotificationHoursState
import co.artise.android.quiet.api.QuietHours
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.launch

/** "Horario de notificaciones" for the workspace on screen. */
@Inject
class NotificationHoursPresenter(
    private val matrixClient: MatrixClient,
    private val quietHours: QuietHours,
) : Presenter<NotificationHoursState> {
    @Composable
    override fun present(): NotificationHoursState {
        val scope = rememberCoroutineScope()
        val settings by remember { quietHours.settings(matrixClient.sessionId) }.collectAsState(NotificationHoursSettings())
        var picking by remember { mutableStateOf<HoursField?>(null) }

        fun save(next: NotificationHoursSettings) {
            scope.launch { quietHours.set(matrixClient.sessionId, next) }
        }

        return NotificationHoursState(
            settings = settings,
            picking = picking,
            eventSink = { event ->
                when (event) {
                    is NotificationHoursEvent.SetEnabled -> save(settings.copy(enabled = event.enabled))
                    is NotificationHoursEvent.SetSchedule -> save(settings.withSchedule(event.schedule))
                    is NotificationHoursEvent.SetDay -> save(settings.withDay(event.day, event.on))
                    is NotificationHoursEvent.SetAllowMentions -> save(settings.copy(allowMentions = event.allow))
                    is NotificationHoursEvent.SetTime -> save(settings.withTime(event.field.day, event.field.isStart, event.minute))
                    is NotificationHoursEvent.Pick -> picking = event.field
                    NotificationHoursEvent.ClosePicker -> picking = null
                }
            },
        )
    }
}
