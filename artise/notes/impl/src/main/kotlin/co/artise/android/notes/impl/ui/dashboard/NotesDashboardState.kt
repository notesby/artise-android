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
    /** Pull to refresh: a sync, then the page again. */
    val isRefreshing: Boolean,
    val problem: DashboardProblem?,
    /** A report page Ari rewrites each period: only Share, and a line that says so. */
    val isReadOnly: Boolean,
    val share: DocumentShareState,
    val eventSink: (NotesDashboardEvent) -> Unit,
)

enum class DashboardProblem {
    LOAD_FAILED,

    /** The note isn't a dashboard (any more): it opens as a note. */
    NOT_A_DASHBOARD,
}

sealed interface NotesDashboardEvent {
    /** Pull to refresh, or try again: a sync first, so an edit made on this phone is in the page. */
    data object Refresh : NotesDashboardEvent
}
