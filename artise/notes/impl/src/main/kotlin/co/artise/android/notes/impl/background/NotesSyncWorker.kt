/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.background

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import co.artise.android.notes.api.NotesBackgroundNetwork
import co.artise.android.notes.api.NotesBackgroundSync
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.workmanager.api.di.MetroWorkerFactory
import io.element.android.libraries.workmanager.api.di.WorkerKey
import timber.log.Timber
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

/** After a notes push: syncs that one chat, in whichever account has its notes, once the allowed connection is there. */
@AssistedInject
class NotesChatSyncWorker(
    @Assisted params: WorkerParameters,
    @ApplicationContext context: Context,
    private val backgroundSync: NotesBackgroundSync,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val roomId = inputData.getString(ROOM_ID)?.let(::RoomId) ?: return Result.failure()
        // Room ids only: never a chat's or a note's name.
        if (backgroundSync.syncChatInAnyAccount(roomId)) Timber.i("Notes: push synced $roomId") else Timber.i("Notes: push skipped $roomId")
        return Result.success()
    }

    @ContributesIntoMap(AppScope::class, binding = binding<MetroWorkerFactory.WorkerInstanceFactory<*>>())
    @WorkerKey(NotesChatSyncWorker::class)
    @AssistedFactory
    interface Factory : MetroWorkerFactory.WorkerInstanceFactory<NotesChatSyncWorker>

    companion object {
        const val ROOM_ID = "room_id"
    }
}

/** Keeps one periodic [NotesSyncWorker], and one [NotesChatSyncWorker] per pushed chat, waiting for the connection the setting allows. */
interface NotesSyncScheduler {
    fun schedule(network: NotesBackgroundNetwork)

    fun scheduleChat(roomId: RoomId, network: NotesBackgroundNetwork)

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

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultNotesSyncScheduler(
    @ApplicationContext private val context: Context,
) : NotesSyncScheduler {
    override fun schedule(network: NotesBackgroundNetwork) {
        val request = PeriodicWorkRequestBuilder<NotesSyncWorker>(NotesSyncScheduler.INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(NotesSyncScheduler.constraintsFor(network))
            .build()
        // UPDATE keeps the schedule and changes the constraint when the setting changes.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NotesSyncScheduler.WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** One job per chat: a newer push replaces a waiting or running one, which then syncs the latest state. */
    override fun scheduleChat(roomId: RoomId, network: NotesBackgroundNetwork) {
        val request = OneTimeWorkRequestBuilder<NotesChatSyncWorker>()
            .setConstraints(NotesSyncScheduler.constraintsFor(network))
            .setInputData(workDataOf(NotesChatSyncWorker.ROOM_ID to roomId.value))
            .apply {
                // Expedited where it runs without a notification (Android 12+); earlier versions would have to show one.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NotesSyncScheduler.WORK_NAME + "_" + roomId.value, ExistingWorkPolicy.REPLACE, request)
    }
}
