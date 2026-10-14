/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.branding

import androidx.compose.ui.graphics.Color
import co.artise.android.workspace.api.WorkspaceBranding
import co.artise.android.workspace.api.WorkspaceConfig
import co.artise.android.workspace.api.WorkspaceFeatures
import co.artise.android.workspace.api.WorkspaceLinks
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkspaceBrandingTest {
    /** No settings: Artise's own look, with Ari, and every screen. */
    @Test
    fun `default look`() {
        val branding = WorkspaceBranding.of("acme.artise.co", null)
        assertThat(branding).isEqualTo(WorkspaceBranding.Default)
        assertThat(branding.assistantName).isEqualTo("Ari")
        assertThat(branding.accent).isNull()
        assertThat(branding.hasFeature(WorkspaceFeatures.STICKERS)).isTrue()
    }

    /** The workspace's name, logo, accent and assistant's name are used; stickers hide only when the list leaves them out. */
    @Test
    fun `workspace look`() {
        val branding = WorkspaceBranding.of(
            "acme.artise.co",
            WorkspaceConfig(
                name = "Acme",
                logoUrl = "https://artise.co/brand/logo-1.png",
                accentColor = 0xFF1F6FEB,
                assistantName = "Max",
                features = setOf("tasks", "booking"),
            )
        )
        assertThat(branding.name).isEqualTo("Acme")
        assertThat(branding.logoUrl).isEqualTo("https://artise.co/brand/logo-1.png")
        assertThat(branding.accent).isEqualTo(Color(0xFF1F6FEB))
        assertThat(branding.assistantName).isEqualTo("Max")
        assertThat(branding.hasFeature(WorkspaceFeatures.STICKERS)).isFalse()
        assertThat(WorkspaceBranding.of("acme.artise.co", WorkspaceConfig(features = setOf("stickers"))).hasFeature(WorkspaceFeatures.STICKERS)).isTrue()
        assertThat(WorkspaceBranding.of("acme.artise.co", WorkspaceConfig(name = "Acme")).assistantName).isEqualTo("Ari")
    }

    /** Notes are offered only with a Notes API link; artise.co has its links until its file publishes them. */
    @Test
    fun `notes need a notes link`() {
        assertThat(WorkspaceBranding.of("acme.artise.co", WorkspaceConfig(name = "Acme")).hasNotes).isFalse()
        val links = WorkspaceLinks("https://web.acme.example", "https://web.acme.example/api/notes/v1", null)
        assertThat(WorkspaceBranding.of("acme.artise.co", WorkspaceConfig(links = links)).hasNotes).isTrue()
        assertThat(WorkspaceBranding.of("artise.co", null).hasNotes).isTrue()
        assertThat(WorkspaceBranding.of("artise.co", null).links?.notesApi).isEqualTo("https://chat.artise.co/api/notes/v1")
        // Never derived from the server name: another server without links has none.
        assertThat(WorkspaceLinks.of("beta.artise.co", WorkspaceConfig())).isNull()
        // Once artise.co publishes links, they win.
        assertThat(WorkspaceLinks.of("artise.co", WorkspaceConfig(links = links))).isEqualTo(links)
    }

    /** The welcome shows once, right after signing in, in the phone's language, else the workspace's, else not at all. */
    @Test
    fun `welcome after the first sign-in`() {
        val config = WorkspaceConfig(welcome = mapOf("en" to "Welcome to Acme", "es" to "Bienvenido"), language = "es")
        assertThat(welcomeText(config, "en", justSignedIn = true, alreadyWelcomed = false)).isEqualTo("Welcome to Acme")
        assertThat(welcomeText(config, "pt", justSignedIn = true, alreadyWelcomed = false)).isEqualTo("Bienvenido")
        assertThat(welcomeText(config.copy(language = null), "pt", justSignedIn = true, alreadyWelcomed = false)).isNull()
        assertThat(welcomeText(config, "en", justSignedIn = false, alreadyWelcomed = false)).isNull()
        assertThat(welcomeText(config, "en", justSignedIn = true, alreadyWelcomed = true)).isNull()
        assertThat(welcomeText(null, "en", justSignedIn = true, alreadyWelcomed = false)).isNull()
    }
}
