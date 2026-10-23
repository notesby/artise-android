/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.dashboard

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import co.artise.android.notes.impl.ui.chart.aDocumentShareState

open class NotesDashboardStatePreviewParam : PreviewParameterProvider<NotesDashboardState> {
    override val values: Sequence<NotesDashboardState>
        get() = sequenceOf(
            aNotesDashboardState(),
            aNotesDashboardState(isLoading = false, problem = DashboardProblem.LOAD_FAILED),
            aNotesDashboardState(isLoading = false, canEdit = false),
        )
}

internal fun aNotesDashboardState(
    isLoading: Boolean = true,
    problem: DashboardProblem? = null,
    canEdit: Boolean = true,
) = NotesDashboardState(
    title = "Octubre 2026",
    html = null,
    isLoading = isLoading,
    problem = problem,
    canEdit = canEdit,
    share = aDocumentShareState(),
    eventSink = {},
)
