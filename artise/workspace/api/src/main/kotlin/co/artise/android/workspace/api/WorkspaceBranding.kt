/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.element.android.libraries.matrix.api.core.SessionId

/**
 * How the signed-in account's workspace looks, for Artise's own screens (Element's screens keep Element's look).
 * [Default] is Artise's own look, used until the workspace says otherwise or when it says nothing.
 */
@Immutable
data class WorkspaceBranding(
    val name: String?,
    val logoUrl: String?,
    val accent: Color?,
    val assistantName: String,
    private val features: Set<String>?,
    /** Null when the server has no web host: then no notes and no delete-account page. */
    val links: WorkspaceLinks?,
) {
    /** Whether the notes screens can be offered (the server has a Notes API). */
    val hasNotes: Boolean get() = links?.notesApi != null

    /** False only when the workspace lists its features and [feature] isn't one of them; unknown names are never hidden. */
    fun hasFeature(feature: String): Boolean = features?.contains(feature) ?: true

    companion object {
        const val DEFAULT_ASSISTANT_NAME = "Ari"

        val Default = WorkspaceBranding(
            name = null,
            logoUrl = null,
            accent = null,
            assistantName = DEFAULT_ASSISTANT_NAME,
            features = null,
            links = null,
        )

        fun of(config: WorkspaceConfig?): WorkspaceBranding = if (config == null) {
            Default
        } else {
            WorkspaceBranding(
                name = config.name,
                logoUrl = config.logoUrl,
                accent = config.accentColor?.let { Color(it) },
                assistantName = config.assistantName ?: DEFAULT_ASSISTANT_NAME,
                features = config.features,
                links = config.links,
            )
        }
    }
}

/** Feature names from the workspace file that match Artise screens on Android. */
object WorkspaceFeatures {
    const val STICKERS = "stickers"
}

/**
 * The signed-in account's workspace look, provided by [WorkspaceBrandingRenderer] around everything signed in. A composition local
 * rather than a parameter, so Element's screens between the root and the Artise parts need no changes.
 */
@Suppress("CompositionLocalAllowlist")
val LocalWorkspaceBranding = staticCompositionLocalOf { WorkspaceBranding.Default }

/** Wraps the signed-in app: provides [LocalWorkspaceBranding] and, once after the first sign-in, shows the workspace's welcome. */
interface WorkspaceBrandingRenderer {
    /**
     * [canWelcome] is false while onboarding steps are still showing, so the welcome comes after them. [onAddWorkspace] opens sign-in for
     * another workspace, from the workspace switcher.
     */
    @Composable
    fun Render(sessionId: SessionId, canWelcome: Boolean, onAddWorkspace: () -> Unit, content: @Composable () -> Unit)
}
