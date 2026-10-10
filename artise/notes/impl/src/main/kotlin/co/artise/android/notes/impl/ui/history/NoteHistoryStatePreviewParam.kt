/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.history

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import co.artise.android.notes.impl.R
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

open class NoteHistoryStatePreviewParam : PreviewParameterProvider<NoteHistoryState> {
    override val values: Sequence<NoteHistoryState>
        get() = sequenceOf(
            aNoteHistoryState(
                HistoryDay(
                    "Today",
                    persistentListOf(
                        aHistoryEntry("v1", "18:02", ChangeLabel.Text(R.string.screen_notes_change_reaction, listOf("Ana")), "Added bread"),
                        aHistoryEntry("v2", "09:15", ChangeLabel.Text(R.string.screen_notes_change_ari, listOf("Luis")), "Weekly list"),
                    ),
                ),
                HistoryDay("Sunday 4 October", persistentListOf(aHistoryEntry("v3", "20:30", ChangeLabel.Plain("synced device"), "edited outside chat"))),
            ),
            aNoteHistoryState(isLoading = true),
            aNoteHistoryState(),
            aNoteHistoryState(failed = true),
        )
}

internal fun aNoteHistoryState(vararg days: HistoryDay, isLoading: Boolean = false, failed: Boolean = false) = NoteHistoryState(
    title = "Súper",
    days = days.toList().toImmutableList(),
    isLoading = isLoading,
    failed = failed,
    eventSink = {},
)
