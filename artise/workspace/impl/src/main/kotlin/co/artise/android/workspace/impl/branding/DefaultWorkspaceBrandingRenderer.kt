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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import co.artise.android.workspace.api.LocalSignOutEndsArtiseId
import co.artise.android.workspace.api.LocalWorkspaceBranding
import co.artise.android.workspace.api.LocalWorkspaceSwitcher
import co.artise.android.workspace.api.OpenWorkspace
import co.artise.android.workspace.api.SignInWorkspaceHolder
import co.artise.android.workspace.api.WorkspaceBranding
import co.artise.android.workspace.api.WorkspaceBrandingRenderer
import co.artise.android.workspace.api.WorkspaceConfig
import co.artise.android.workspace.api.WorkspaceConfigRepository
import co.artise.android.workspace.api.WorkspaceSwitcher
import co.artise.android.workspace.api.serverName
import co.artise.android.workspace.impl.artiseid.ArtiseIdAuth
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.designsystem.components.dialogs.AlertDialog
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import co.artise.android.workspace.api.R as ApiR

@ContributesBinding(AppScope::class)
class DefaultWorkspaceBrandingRenderer(
    @ApplicationContext private val context: Context,
    private val configRepository: WorkspaceConfigRepository,
    private val signInWorkspaceHolder: SignInWorkspaceHolder,
    private val artiseIdAuth: ArtiseIdAuth,
    private val sessionStore: SessionStore,
    private val buildMeta: BuildMeta,
) : WorkspaceBrandingRenderer {
    private val signOutEndsArtiseId: suspend () -> Boolean = { signOutEndsArtiseId(sessionStore, artiseIdAuth) }

    private val preferences by lazy { context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }

    @Composable
    override fun Render(sessionId: SessionId, canWelcome: Boolean, onAddWorkspace: () -> Unit, content: @Composable () -> Unit) {
        val flow = remember(sessionId) { configRepository.configFlow(sessionId) }
        val config by flow.collectAsState(initial = remember(sessionId) { configRepository.cached(sessionId) })
        val branding = remember(sessionId, config) { WorkspaceBranding.of(config) }
        val sessions by remember { sessionStore.sessionsFlow() }.collectAsState(initial = emptyList())
        val scope = rememberCoroutineScope()
        val latestOnAdd by rememberUpdatedState(onAddWorkspace)
        val switcher = remember(sessions, sessionId, config) {
            WorkspaceSwitcher(
                workspaces = sessions.sortedBy { it.position }.map { session ->
                    val id = SessionId(session.userId)
                    val workspace = if (id == sessionId) config else configRepository.cached(id)
                    OpenWorkspace(sessionId = id, name = workspace?.name ?: id.serverName(), logoUrl = workspace?.logoUrl)
                }.toImmutableList(),
                current = sessionId,
                switchTo = { scope.launch { sessionStore.setLatestSession(it.value) } },
                add = { latestOnAdd() },
            )
        }
        CompositionLocalProvider(
            LocalWorkspaceBranding provides branding,
            LocalSignOutEndsArtiseId provides signOutEndsArtiseId,
            LocalWorkspaceSwitcher provides switcher,
        ) {
            content()
            if (canWelcome) Welcome(sessionId, config)
            UpdateNotice(config)
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

    /** When this app is older than the workspace's minimum: "Actualiza Artise para seguir usando {espacio}", with the store link. */
    @Composable
    private fun UpdateNotice(config: WorkspaceConfig?) {
        if (config == null || !config.needsAppUpdate(buildMeta.versionCode)) return
        var dismissed by remember(config.minAndroidVersionCode) { mutableStateOf(false) }
        if (dismissed) return
        val uriHandler = LocalUriHandler.current
        val storeLink = config.androidStoreLink ?: PLAY_STORE_LINK
        ConfirmationDialog(
            title = stringResource(ApiR.string.artise_update_title),
            content = stringResource(ApiR.string.artise_update_message, config.name ?: "Artise"),
            submitText = stringResource(ApiR.string.artise_update_action),
            onSubmitClick = { uriHandler.openUri(storeLink) },
            onDismiss = { dismissed = true },
        )
    }

    private fun SessionId.welcomedKey() = "$value.welcomed"

    private companion object {
        const val PREFERENCES = "artise_workspace_welcome"
        const val PLAY_STORE_LINK = "https://play.google.com/store/apps/details?id=co.artise.app"
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
