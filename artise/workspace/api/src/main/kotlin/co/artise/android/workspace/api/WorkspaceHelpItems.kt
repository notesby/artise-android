/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.api

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text
import timber.log.Timber

/**
 * Settings' "Ayuda": the workspace's help page, and a chat with its admin (their profile, with "Enviar mensaje"). Each shows only when
 * the workspace file names it.
 */
@Composable
fun ColumnScope.WorkspaceHelpItems(modifier: Modifier = Modifier) {
    val branding = LocalWorkspaceBranding.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    branding.links?.help?.let { help ->
        ListItem(
            modifier = modifier,
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Help())),
            onClick = { uriHandler.openUri(help) },
        ) {
            Text(stringResource(R.string.artise_help))
        }
    }
    branding.contactUserId?.let { admin ->
        ListItem(
            modifier = modifier,
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.ChatProblem())),
            onClick = {
                // The app's own user link opens the admin's profile, where "Enviar mensaje" starts or opens the chat.
                val intent = Intent(Intent.ACTION_VIEW, "element://user/$admin".toUri()).setPackage(context.packageName)
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Timber.w(e, "Couldn't open the admin's profile")
                }
            },
        ) {
            Text(stringResource(R.string.artise_help_write_admin))
        }
    }
}
