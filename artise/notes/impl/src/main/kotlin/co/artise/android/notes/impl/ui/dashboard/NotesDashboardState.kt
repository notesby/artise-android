/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.dashboard

import androidx.compose.runtime.Immutable
import co.artise.android.notes.impl.share.DocumentShareState

/** A dashboard (or a business's report page) as the server draws it, with Edit and Share. */
@Immutable
data class NotesDashboardState(
    val title: String,
    /** The page, once loaded. */
    val html: String?,
    val isLoading: Boolean,
    val problem: DashboardProblem?,
    /** Not for a report page Ari rewrites each period: only Share. */
    val canEdit: Boolean,
    val share: DocumentShareState,
    val eventSink: (NotesDashboardEvent) -> Unit,
)

enum class DashboardProblem {
    LOAD_FAILED,

    /** The note isn't a dashboard (any more): it opens as a note. */
    NOT_A_DASHBOARD,
}

sealed interface NotesDashboardEvent {
    data object Retry : NotesDashboardEvent
}
