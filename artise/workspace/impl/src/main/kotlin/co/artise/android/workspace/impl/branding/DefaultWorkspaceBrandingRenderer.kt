/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.branding

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.content.edit
import co.artise.android.workspace.api.LocalSignOutEndsArtiseId
import co.artise.android.workspace.api.LocalWorkspaceBranding
import co.artise.android.workspace.api.SignInWorkspaceHolder
import co.artise.android.workspace.api.WorkspaceBranding
import co.artise.android.workspace.api.WorkspaceBrandingRenderer
import co.artise.android.workspace.api.WorkspaceConfig
import co.artise.android.workspace.api.WorkspaceConfigRepository
import co.artise.android.workspace.api.serverName
import co.artise.android.workspace.impl.artiseid.ArtiseIdAuth
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.designsystem.components.dialogs.AlertDialog
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.sessionstorage.api.SessionStore

@ContributesBinding(AppScope::class)
class DefaultWorkspaceBrandingRenderer(
    @ApplicationContext private val context: Context,
    private val configRepository: WorkspaceConfigRepository,
    private val signInWorkspaceHolder: SignInWorkspaceHolder,
    private val artiseIdAuth: ArtiseIdAuth,
    private val sessionStore: SessionStore,
) : WorkspaceBrandingRenderer {
    private val signOutEndsArtiseId: suspend () -> Boolean = { signOutEndsArtiseId(sessionStore, artiseIdAuth) }

    private val preferences by lazy { context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }

    @Composable
    override fun Render(sessionId: SessionId, canWelcome: Boolean, content: @Composable () -> Unit) {
        val flow = remember(sessionId) { configRepository.configFlow(sessionId) }
        val config by flow.collectAsState(initial = remember(sessionId) { configRepository.cached(sessionId) })
        val branding = remember(sessionId, config) { WorkspaceBranding.of(config) }
        CompositionLocalProvider(
            LocalWorkspaceBranding provides branding,
            LocalSignOutEndsArtiseId provides signOutEndsArtiseId,
        ) {
            content()
            if (canWelcome) Welcome(sessionId, config)
        }
    }

    /** Once, after this account's first sign-in on this phone: the workspace's welcome, in the phone's language or the workspace's. */
    @Composable
    private fun Welcome(sessionId: SessionId, config: WorkspaceConfig?) {
        val language = LocalConfiguration.current.locales[0].language
        var dismissed by remember(sessionId) { mutableStateOf(false) }
        val signedInTo = signInWorkspaceHolder.current.collectAsState().value?.serverName
        val text = welcomeText(
            config = config,
            language = language,
            justSignedIn = signedInTo == sessionId.serverName(),
            alreadyWelcomed = dismissed || preferences.getBoolean(sessionId.welcomedKey(), false),
        ) ?: return
        AlertDialog(
            title = config?.name,
            content = text,
            onDismiss = {
                preferences.edit { putBoolean(sessionId.welcomedKey(), true) }
                dismissed = true
            },
        )
    }

    private fun SessionId.welcomedKey() = "$value.welcomed"

    private companion object {
        const val PREFERENCES = "artise_workspace_welcome"
    }
}

/**
 * The welcome to show, if any: only right after signing in to this account's workspace on this phone (not to accounts that were
 * already signed in), once, in the phone's language or else the workspace's.
 */
internal fun welcomeText(config: WorkspaceConfig?, language: String, justSignedIn: Boolean, alreadyWelcomed: Boolean): String? =
    config?.welcomeFor(language)?.takeIf { justSignedIn && !alreadyWelcomed }

/** Signing out of the last workspace also forgets Artise ID (ArtiseIdSessionCleanup), so sign-out says so when Artise ID is signed in. */
internal suspend fun signOutEndsArtiseId(sessionStore: SessionStore, artiseIdAuth: ArtiseIdAuth): Boolean =
    sessionStore.numberOfSessions() <= 1 && artiseIdAuth.hasTokens()
