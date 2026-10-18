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
import co.artise.android.quiet.api.QuietHours
import co.artise.android.quiet.api.QuietHoursEvent
import co.artise.android.quiet.api.QuietHoursSettings
import co.artise.android.quiet.api.QuietHoursState
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.launch

/** "Silenciar fuera de horario" for the workspace on screen. */
@Inject
class QuietHoursPresenter(
    private val matrixClient: MatrixClient,
    private val quietHours: QuietHours,
) : Presenter<QuietHoursState> {
    @Composable
    override fun present(): QuietHoursState {
        val scope = rememberCoroutineScope()
        val settings by remember { quietHours.settings(matrixClient.sessionId) }.collectAsState(QuietHoursSettings())
        var isPickerVisible by remember { mutableStateOf(false) }
        return QuietHoursState(
            settings = settings,
            isPickerVisible = isPickerVisible,
            eventSink = { event ->
                when (event) {
                    is QuietHoursEvent.Change -> scope.launch { quietHours.set(matrixClient.sessionId, event.settings) }
                    QuietHoursEvent.OpenPicker -> isPickerVisible = true
                    QuietHoursEvent.ClosePicker -> isPickerVisible = false
                }
            },
        )
    }
}
