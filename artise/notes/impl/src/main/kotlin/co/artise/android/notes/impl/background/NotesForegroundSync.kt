/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.background

import android.app.Activity
import co.artise.android.notes.api.NotesBackgroundSettings
import co.artise.android.notes.api.NotesBackgroundSync
import co.artise.android.notes.impl.sync.NotesLiveUpdates
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.features.enterprise.api.AppStartupHook
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.services.appnavstate.api.AppForegroundStateService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * While the app is open, on any screen: follows the notes state of every chat whose notes are on this phone (a changed tree syncs
 * that chat), and every 5 minutes syncs them all (an unchanged chat costs a `304`). Both only on a connection the setting allows.
 */
@SingleIn(AppScope::class)
@Inject
class NotesForegroundSync(
    private val appForegroundStateService: AppForegroundStateService,
    private val accounts: NotesAccounts,
    private val backgroundSync: NotesBackgroundSync,
) {
    private val started = AtomicBoolean(false)

    /** Starts following in [scope], once for the life of the app. */
    fun start(scope: CoroutineScope) {
        if (started.compareAndSet(false, true)) scope.launch { run() }
    }

    private suspend fun run() {
        appForegroundStateService.isInForeground.collectLatest { inForeground ->
            if (!inForeground) return@collectLatest
            coroutineScope {
                for (sessionId in accounts.withNotes()) {
                    val account = accounts.open(sessionId) ?: continue
                    launch { keepUpToDate(account) }
                }
            }
        }
    }

    /** Until cancelled, for one account. */
    internal suspend fun keepUpToDate(account: NotesAccount, interval: Duration = CHECK_INTERVAL) = coroutineScope {
        val repository = account.repository
        val chats = MutableStateFlow(repository.cachedChats().map { it.roomId }.toSet())
        launch {
            chats.collectLatest { roomIds ->
                NotesLiveUpdates(account.client, repository).keepUpToDate(roomIds, isAllowed = backgroundSync::isAllowedNow)
            }
        }
        while (true) {
            delay(interval)
            // Chats opened in Notas since the last check are followed from now on.
            val roomIds: Set<RoomId> = repository.cachedChats().map { it.roomId }.toSet()
            chats.value = roomIds
            if (backgroundSync.isAllowedNow()) roomIds.forEach { repository.sync(it) }
        }
    }

    companion object {
        val CHECK_INTERVAL = 5.minutes
    }
}

/**
 * At app start: schedules the job for while the app is closed, with the current setting, and starts [NotesForegroundSync] for the
 * life of the app.
 */
@ContributesIntoSet(AppScope::class)
class NotesBackgroundStartup(
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val settings: NotesBackgroundSettings,
    private val scheduler: NotesSyncScheduler,
    private val foregroundSync: NotesForegroundSync,
) : AppStartupHook {
    override suspend fun onAppStartup(activity: Activity) {
        scheduler.schedule(settings.network.value)
        foregroundSync.start(appScope)
    }
}
