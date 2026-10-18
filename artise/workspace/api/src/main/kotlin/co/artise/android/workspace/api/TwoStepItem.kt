/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.api

import android.app.Activity
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.androidutils.browser.openUrlInChromeCustomTab
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text

/** The person's Artise ID protection, from Artise ID: two-step on or off, and the provider they sign in with (google, microsoft, apple). */
@Immutable
data class TwoStepStatus(val isOn: Boolean, val provider: String?)

/** How Settings reads [TwoStepStatus] and where it's changed (Artise ID's own page); null [pageUrl] when not signed in with Artise ID. */
@Immutable
data class TwoStepAccess(
    val load: suspend () -> TwoStepStatus?,
    val pageUrl: suspend () -> String?,
)

/** Provided by [WorkspaceBrandingRenderer] next to [LocalWorkspaceBranding]. */
@Suppress("CompositionLocalAllowlist")
val LocalTwoStepAccess = staticCompositionLocalOf { TwoStepAccess(load = { null }, pageUrl = { null }) }

/**
 * Settings' "Verificación en dos pasos": On, Off, or "Tu cuenta de Google protege tu inicio de sesión" for provider sign-ins (Artise ID
 * would also ask them, so turning it on isn't offered there). Opens Artise ID's page in the browser tab used for signing in. Only for
 * people signed in with Artise ID.
 */
@Composable
fun ColumnScope.TwoStepItem(modifier: Modifier = Modifier) {
    val access = LocalTwoStepAccess.current
    val pageUrl by produceState<String?>(null, access) { value = access.pageUrl() }
    val status by produceState<TwoStepStatus?>(null, access) { value = access.load() }
    val url = pageUrl ?: return
    val current = status ?: return
    val activity = LocalContext.current as? Activity
    val provider = current.provider?.let(::providerName)
    ListItem(
        modifier = modifier,
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Lock())),
        supportingContent = {
            Text(
                when {
                    provider != null -> stringResource(R.string.artise_two_step_provider, provider)
                    current.isOn -> stringResource(R.string.artise_two_step_on)
                    else -> stringResource(R.string.artise_two_step_off)
                }
            )
        },
        onClick = if (provider == null) {
            { activity?.openUrlInChromeCustomTab(null, false, url) }
        } else {
            null
        },
    ) {
        Text(stringResource(R.string.artise_two_step_title))
    }
}

private fun providerName(key: String): String? = when (key) {
    "google" -> "Google"
    "microsoft" -> "Microsoft"
    "apple" -> "Apple"
    else -> null
}
