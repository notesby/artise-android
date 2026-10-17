/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * Whether signing out of the workspace on screen also ends the Artise ID session on this phone: true when it's the last workspace
 * and the person signed in with Artise ID. Provided by [WorkspaceBrandingRenderer] next to [LocalWorkspaceBranding].
 */
@Suppress("CompositionLocalAllowlist")
val LocalSignOutEndsArtiseId = staticCompositionLocalOf<suspend () -> Boolean> { { false } }

/** Sign-out's texts, naming the workspace when its name is known ("Cerrar sesión en Panadería Sol"). */
data class SignOutTexts(
    val title: String,
    val content: String,
    val submit: String,
)

/** The sign-out item in Settings: "Cerrar sesión en {workspace}", or "Cerrar sesión" while the workspace's name isn't known. */
@Composable
fun signOutLabel(): String {
    val name = LocalWorkspaceBranding.current.name
    return if (name.isNullOrBlank()) stringResource(CommonStrings.action_signout) else stringResource(R.string.artise_sign_out_from, name)
}

/** The sign-out confirmation, with the Artise ID sentence added when this is the last workspace signed in with Artise ID. */
@Composable
fun signOutConfirmationTexts(): SignOutTexts {
    val name = LocalWorkspaceBranding.current.name
    val endsArtiseId = LocalSignOutEndsArtiseId.current
    val alsoArtiseId by produceState(initialValue = false, endsArtiseId) { value = endsArtiseId() }
    val content = if (name.isNullOrBlank()) {
        stringResource(R.string.artise_sign_out_content_unnamed)
    } else {
        stringResource(R.string.artise_sign_out_content, name)
    }
    return SignOutTexts(
        title = if (name.isNullOrBlank()) stringResource(CommonStrings.action_signout) else stringResource(R.string.artise_sign_out_title, name),
        content = if (alsoArtiseId) content + " " + stringResource(R.string.artise_sign_out_artise_id) else content,
        submit = stringResource(CommonStrings.action_signout),
    )
}
