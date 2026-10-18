/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import co.artise.android.notes.impl.R
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.dialogs.ListDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.list.TextFieldListItem
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Edits a link tapped in the editor: which note (or web address) it goes to, and the text shown for it.
 * Note links get the same suggestions as typing `[[`. "Remove link" keeps the text and drops the link.
 */
@Composable
fun LinkEditDialog(
    edit: LinkEditState,
    notePaths: ImmutableList<String>,
    onSave: (EditableLink) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    var target by rememberSaveable { mutableStateOf(edit.link.target) }
    var shownText by rememberSaveable { mutableStateOf(edit.link.shownText) }
    val isNote = edit.link.isNote
    val suggestions = if (isNote) {
        WikiLinkSuggestions.suggestionsFor(target, notePaths, currentPath = null).filter { it.linkText != target.trim() }.take(4)
    } else {
        emptyList()
    }
    ListDialog(
        title = stringResource(R.string.screen_notes_link_edit_title),
        onSubmit = { onSave(EditableLink(isNote, target, shownText)) },
        onDismissRequest = onDismiss,
        modifier = modifier,
        submitText = stringResource(CommonStrings.action_save),
        enabled = target.isNotBlank(),
    ) {
        item {
            TextFieldListItem(
                label = stringResource(if (isNote) R.string.screen_notes_link_edit_note else R.string.screen_notes_link_edit_address),
                placeholder = null,
                text = target,
                onTextChange = { target = it },
            )
        }
        items(suggestions, key = { it.path }) { suggestion ->
            ListItem(
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Document())),
                onClick = { target = suggestion.linkText },
            ) {
                Text(suggestion.linkText)
            }
        }
        item {
            TextFieldListItem(
                label = stringResource(R.string.screen_notes_link_edit_text),
                placeholder = null,
                text = shownText,
                onTextChange = { shownText = it },
            )
        }
        if (!isNote && target.isNotBlank()) {
            item {
                ListItem(
                    leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.PopOut())),
                    // "www.example.com" works too: browsers need the https:// in front.
                    onClick = { uriHandler.openUri(if (target.contains("://")) target.trim() else "https://" + target.trim()) },
                ) {
                    Text(stringResource(R.string.screen_notes_link_edit_open))
                }
            }
        }
        // A link being added has nothing to remove yet.
        if (!edit.isNew) {
            item {
                ListItem(
                    leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Close())),
                    style = ListItemStyle.Destructive,
                    onClick = onRemove,
                ) {
                    Text(stringResource(R.string.screen_notes_link_edit_remove))
                }
            }
        }
    }
}

internal class LinkEditStatePreviewParam : PreviewParameterProvider<LinkEditState> {
    override val values: Sequence<LinkEditState>
        get() = sequenceOf(
            LinkEditState(0, 24, EditableLink(isNote = true, target = "Mo", shownText = "el mole"), isNew = false),
            LinkEditState(0, 30, EditableLink(isNote = false, target = "https://artise.co", shownText = "Artise"), isNew = true),
        )
}

@PreviewsDayNight
@Composable
internal fun LinkEditDialogPreview(@PreviewParameter(LinkEditStatePreviewParam::class) edit: LinkEditState) = ElementPreview {
    LinkEditDialog(
        edit = edit,
        notePaths = persistentListOf("Recetas/Mole.md", "Viejo/Mole.md", "Súper.md"),
        onSave = {},
        onRemove = {},
        onDismiss = {},
    )
}
