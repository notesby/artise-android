/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.api

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.collections.immutable.persistentListOf

/** Opened from the workspace's name on the chat list: every workspace on this phone, the one on screen ticked, and "Añadir…". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceSwitcherSheet(
    switcher: WorkspaceSwitcher,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, scrollable = true, modifier = modifier) {
        Text(
            text = stringResource(R.string.artise_workspaces_title),
            style = ElementTheme.typography.fontHeadingSmMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        switcher.workspaces.forEach { workspace ->
            ListItem(
                leadingContent = workspace.logoUrl?.let { url ->
                    ListItemContent.Custom { AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(32.dp)) }
                },
                trailingContent = if (workspace.sessionId == switcher.current) ListItemContent.Icon(IconSource.Vector(CompoundIcons.Check())) else null,
                onClick = {
                    onDismiss()
                    if (workspace.sessionId != switcher.current) switcher.switchTo(workspace.sessionId)
                },
            ) {
                Text(workspace.name)
            }
        }
        ListItem(
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Plus())),
            onClick = {
                onDismiss()
                switcher.add()
            },
        ) {
            Text(stringResource(R.string.artise_workspaces_add))
        }
    }
}

@PreviewsDayNight
@Composable
internal fun WorkspaceSwitcherSheetPreview() = ElementPreview {
    WorkspaceSwitcherSheet(
        switcher = WorkspaceSwitcher(
            workspaces = persistentListOf(
                OpenWorkspace(SessionId("@ana:familia.artise.co"), "Familia Moreno", null),
                OpenWorkspace(SessionId("@ana:sol.artise.co"), "Panadería Sol", null),
            ),
            current = SessionId("@ana:familia.artise.co"),
            switchTo = {},
            add = {},
        ),
        onDismiss = {},
    )
}
