/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.accountdeletion

import android.content.Context
import androidx.core.content.edit
import co.artise.android.workspace.api.WorkspaceConfig
import co.artise.android.workspace.api.WorkspaceConfigRepository
import co.artise.android.workspace.api.WorkspaceLinks
import co.artise.android.workspace.api.serverName
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Deleting the account happens on Artise's web page (Google Play requires a way from inside the app). The page does
 * everything and ends the app's session on the server; the app only opens it, and afterwards goes straight back to
 * sign-in instead of explaining why it was signed out.
 */
interface AccountDeletion {
    /** The account's workspace page (its `links.delete_account`), in Spanish when the app is; null when its server has none. */
    fun pageUrl(sessionId: SessionId, language: String): String?

    /** Whether the account's workspace has the page, so Settings offers "Delete account". */
    fun hasPageFlow(sessionId: SessionId): Flow<Boolean>

    /** The page was opened: a sign-out that follows soon is the deletion. */
    fun markStarted()

    /** Whether the session just ended because the account was deleted; true once, then forgotten. */
    fun consumeStarted(): Boolean

    companion object {
        /** Long enough to read the page and confirm; a later sign-out has another cause. */
        const val STARTED_VALID_MILLIS = 60 * 60 * 1000L
    }
}

/** Where the "deletion started" time is kept, so it survives the app being closed while the browser is open. */
interface AccountDeletionStore {
    var startedAt: Long?
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultAccountDeletion(
    private val store: AccountDeletionStore,
    private val clock: SystemClock,
    private val workspaceConfigRepository: WorkspaceConfigRepository,
) : AccountDeletion {
    override fun pageUrl(sessionId: SessionId, language: String): String? =
        pageOf(sessionId, workspaceConfigRepository.cached(sessionId))?.let { page ->
            if (language == "es") page + (if ('?' in page) "&" else "?") + "lang=es" else page
        }

    override fun hasPageFlow(sessionId: SessionId): Flow<Boolean> =
        workspaceConfigRepository.configFlow(sessionId).map { pageOf(sessionId, it) != null }

    private fun pageOf(sessionId: SessionId, config: WorkspaceConfig?): String? = WorkspaceLinks.of(sessionId.serverName(), config)?.deleteAccount

    override fun markStarted() {
        store.startedAt = clock.epochMillis()
    }

    override fun consumeStarted(): Boolean {
        val startedAt = store.startedAt ?: return false
        store.startedAt = null
        return clock.epochMillis() - startedAt in 0..AccountDeletion.STARTED_VALID_MILLIS
    }
}

@ContributesBinding(AppScope::class)
class SharedPreferencesAccountDeletionStore(
    @ApplicationContext context: Context,
) : AccountDeletionStore {
    private val preferences = context.getSharedPreferences("artise_account_deletion", Context.MODE_PRIVATE)

    override var startedAt: Long?
        get() = preferences.getLong(KEY_STARTED_AT, -1L).takeIf { it >= 0 }
        set(value) = preferences.edit { if (value == null) remove(KEY_STARTED_AT) else putLong(KEY_STARTED_AT, value) }

    private companion object {
        const val KEY_STARTED_AT = "started_at"
    }
}
