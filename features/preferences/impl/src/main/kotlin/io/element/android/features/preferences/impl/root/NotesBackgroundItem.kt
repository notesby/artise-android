/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import co.artise.android.notes.api.NotesBackgroundNetwork
import co.artise.android.notes.api.R
import co.artise.android.workspace.api.LocalWorkspaceBranding
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.dialogs.SingleSelectionDialog
import io.element.android.libraries.designsystem.components.dialogs.listOptionOf
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * Artise: Ajustes → "Actualizar notas en segundo plano", for this phone: only on Wi-Fi, or Wi-Fi and mobile data. Shown only when
 * the workspace has notes.
 */
@Composable
fun NotesBackgroundItem(
    network: NotesBackgroundNetwork,
    onChange: (NotesBackgroundNetwork) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!LocalWorkspaceBranding.current.hasNotes) return
    var isChoosing by remember { mutableStateOf(false) }
    val options = listOptionOf(
        stringResource(R.string.screen_artise_notes_background_wifi),
        stringResource(R.string.screen_artise_notes_background_mobile),
    )
    ListItem(
        modifier = modifier,
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Cloud())),
        supportingContent = { Text(options[network.ordinal].title) },
        onClick = { isChoosing = true },
    ) {
        Text(stringResource(R.string.screen_artise_notes_background_title))
    }
    if (isChoosing) {
        SingleSelectionDialog(
            title = stringResource(R.string.screen_artise_notes_background_title),
            subtitle = stringResource(R.string.screen_artise_notes_background_footer),
            options = options,
            initialSelection = network.ordinal,
            onSelectOption = { index ->
                onChange(NotesBackgroundNetwork.entries[index])
                isChoosing = false
            },
            onDismissRequest = { isChoosing = false },
        )
    }
}

@PreviewsDayNight
@Composable
internal fun NotesBackgroundItemPreview() = ElementPreview {
    NotesBackgroundItem(network = NotesBackgroundNetwork.WIFI_ONLY, onChange = {})
}
