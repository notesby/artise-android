/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import co.artise.android.notes.api.NotesBackgroundNetwork
import co.artise.android.notes.api.NotesBackgroundSync
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.workmanager.api.di.MetroWorkerFactory
import io.element.android.libraries.workmanager.api.di.WorkerKey
import java.util.concurrent.TimeUnit

/** While the app is closed: every 15 minutes (Android's shortest), the chats whose notes are on this phone sync. */
@AssistedInject
class NotesSyncWorker(
    @Assisted params: WorkerParameters,
    @ApplicationContext context: Context,
    private val backgroundSync: NotesBackgroundSync,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        backgroundSync.syncAll()
        // A chat that didn't sync tries again next time; retrying sooner would only spend data.
        return Result.success()
    }

    @ContributesIntoMap(AppScope::class, binding = binding<MetroWorkerFactory.WorkerInstanceFactory<*>>())
    @WorkerKey(NotesSyncWorker::class)
    @AssistedFactory
    interface Factory : MetroWorkerFactory.WorkerInstanceFactory<NotesSyncWorker>
}

/** Keeps one periodic [NotesSyncWorker], waiting for the connection the setting allows. */
@SingleIn(AppScope::class)
@Inject
class NotesSyncScheduler(
    @ApplicationContext private val context: Context,
) {
    fun schedule(network: NotesBackgroundNetwork) {
        val request = PeriodicWorkRequestBuilder<NotesSyncWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraintsFor(network))
            .build()
        // UPDATE keeps the schedule and changes the constraint when the setting changes.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    companion object {
        const val WORK_NAME = "artise_notes_sync"
        const val INTERVAL_MINUTES = 15L

        /** "Solo con Wi-Fi" waits for a connection that isn't metered; "Wi-Fi y datos móviles" for any. */
        fun constraintsFor(network: NotesBackgroundNetwork): Constraints = Constraints.Builder()
            .setRequiredNetworkType(
                when (network) {
                    NotesBackgroundNetwork.WIFI_ONLY -> NetworkType.UNMETERED
                    NotesBackgroundNetwork.WIFI_AND_MOBILE -> NetworkType.CONNECTED
                }
            )
            .build()
    }
}
