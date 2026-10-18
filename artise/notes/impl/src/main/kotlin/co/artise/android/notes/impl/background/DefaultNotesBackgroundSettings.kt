/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.background

import android.content.Context
import androidx.core.content.edit
import co.artise.android.notes.api.NotesBackgroundNetwork
import co.artise.android.notes.api.NotesBackgroundSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Kept on this phone for every account. A change re-schedules the job that runs while the app is closed. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultNotesBackgroundSettings(
    @ApplicationContext context: Context,
    private val scheduler: NotesSyncScheduler,
) : NotesBackgroundSettings {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val current = MutableStateFlow(
        preferences.getString(KEY, null)?.let { saved -> NotesBackgroundNetwork.entries.firstOrNull { it.name == saved } }
            ?: NotesBackgroundNetwork.WIFI_ONLY
    )

    override val network: StateFlow<NotesBackgroundNetwork> = current

    override fun set(network: NotesBackgroundNetwork) {
        preferences.edit { putString(KEY, network.name) }
        current.value = network
        scheduler.schedule(network)
    }

    private companion object {
        const val PREFERENCES = "artise_notes_background"
        const val KEY = "network"
    }
}
