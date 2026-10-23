/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

import androidx.compose.runtime.Immutable
import co.artise.android.notes.api.NotesPreview
import co.artise.android.notes.impl.dashboard.DashboardBlock
import co.artise.android.notes.impl.dashboard.DashboardProblem
import co.artise.android.notes.impl.dashboard.DashboardTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

/**
 * A dashboard in the editor (a note whose front matter says `type: dashboard`): its body laid out as the server draws
 * it, with its charts' pictures, what's wrong with it, and the charts that can be added. The text stays the source:
 * every change here is a change to it.
 */
@Immutable
data class DashboardEditing(
    val blocks: ImmutableList<DashboardBlock>,
    val problems: ImmutableList<DashboardProblem>,
    /** Each chart's picture by path, as the layout asks for them. */
    val previews: ImmutableMap<String, NotesPreview>,
    /** The chat's charts, for + Chart, by title. */
    val charts: ImmutableList<ChartChoice>,
    val theme: DashboardTheme,
    /** The layout (charts drawn), or the Markdown text as in any note. */
    val showLayout: Boolean,
)

@Immutable
data class ChartChoice(val path: String, val title: String)
