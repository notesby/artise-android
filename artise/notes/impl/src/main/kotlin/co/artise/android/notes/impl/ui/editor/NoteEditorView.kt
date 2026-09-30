/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import co.artise.android.notes.impl.R
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.components.dialogs.SaveChangesDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.DropdownMenu
import io.element.android.libraries.designsystem.theme.components.DropdownMenuItem
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.LinearProgressIndicator
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun NoteEditorView(
    state: NoteEditorState,
    modifier: Modifier = Modifier,
) {
    BackHandler { state.eventSink(NoteEditorEvent.Back) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                titleStr = state.title,
                navigationIcon = { BackButton(onClick = { state.eventSink(NoteEditorEvent.Back) }) },
                actions = {
                    IconButton(onClick = { state.eventSink(NoteEditorEvent.Undo) }, enabled = state.canUndo) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.a11y_notes_undo))
                    }
                    IconButton(onClick = { state.eventSink(NoteEditorEvent.Redo) }, enabled = state.canRedo) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.a11y_notes_redo))
                    }
                    TextButton(
                        text = stringResource(CommonStrings.action_save),
                        onClick = { state.eventSink(NoteEditorEvent.Save) },
                        enabled = !state.isLoading,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .fillMaxSize()
        ) {
            if (state.isResolvingConflict) {
                Text(
                    text = stringResource(R.string.screen_notes_editor_combine_hint),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            BasicTextField(
                value = state.value,
                onValueChange = { state.eventSink(NoteEditorEvent.ValueChanged(it)) },
                visualTransformation = LivePreviewTransformation(state.rawLines, livePreviewStyles()),
                enabled = !state.isLoading,
                // Roomy lines, like the reading view, so editing doesn't feel cramped.
                textStyle = ElementTheme.typography.fontBodyLgRegular.copy(color = ElementTheme.colors.textPrimary, lineHeight = 1.6.em),
                cursorBrush = SolidColor(ElementTheme.colors.textPrimary),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
            )
            if (state.isAttaching) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.screen_notes_attach_uploading),
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            HorizontalDivider()
            if (state.suggestions.isEmpty()) {
                FormattingToolbar(
                    enabled = !state.isLoading,
                    canAttach = !state.isLoading && !state.isAttaching,
                    onAction = { state.eventSink(NoteEditorEvent.Format(it)) },
                    onAttach = { state.eventSink(NoteEditorEvent.Attach(it)) },
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(state.suggestions, key = { it.path }) { suggestion ->
                        ListItem(
                            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Link())),
                            supportingContent = if (suggestion.linkText == suggestion.name) {
                                null
                            } else {
                                { Text(suggestion.path) }
                            },
                            onClick = { state.eventSink(NoteEditorEvent.SelectSuggestion(suggestion)) },
                        ) {
                            Text(suggestion.name)
                        }
                    }
                }
            }
        }
    }
    state.linkEdit?.let { edit ->
        LinkEditDialog(
            edit = edit,
            notePaths = state.notePaths,
            onSave = { state.eventSink(NoteEditorEvent.SaveLink(it)) },
            onRemove = { state.eventSink(NoteEditorEvent.RemoveLink) },
            onDismiss = { state.eventSink(NoteEditorEvent.DismissLinkEdit) },
        )
    }
    state.attachError?.let { error ->
        ErrorDialog(
            content = stringResource(
                when (error) {
                    AttachError.OFFLINE -> R.string.screen_notes_attach_offline
                    AttachError.TOO_BIG -> R.string.screen_notes_attach_too_big
                    AttachError.OTHER -> R.string.screen_notes_attach_failed
                }
            ),
            title = null,
            onSubmit = { state.eventSink(NoteEditorEvent.DismissAttachError) },
        )
    }
    if (state.showSaveChangesDialog) {
        SaveChangesDialog(
            onSaveClick = { state.eventSink(NoteEditorEvent.Save) },
            onDiscardClick = { state.eventSink(NoteEditorEvent.DiscardChanges) },
            onDismiss = { state.eventSink(NoteEditorEvent.DismissSaveChangesDialog) },
        )
    }
}

/** Live preview's looks, from the theme: headings as the reading view shows them, links in link colour. */
@Composable
private fun livePreviewStyles(): LivePreviewStyles {
    val typography = ElementTheme.typography
    val colors = ElementTheme.colors
    return LivePreviewStyles(
        heading1 = SpanStyle(fontSize = typography.fontHeadingLgBold.fontSize, fontWeight = FontWeight.Bold),
        heading2 = SpanStyle(fontSize = typography.fontHeadingMdBold.fontSize, fontWeight = FontWeight.Bold),
        heading3 = SpanStyle(fontSize = typography.fontHeadingSmMedium.fontSize, fontWeight = FontWeight.Medium),
        bold = SpanStyle(fontWeight = FontWeight.Bold),
        italic = SpanStyle(fontStyle = FontStyle.Italic),
        strikethrough = SpanStyle(textDecoration = TextDecoration.LineThrough),
        code = SpanStyle(fontFamily = FontFamily.Monospace, background = colors.bgSubtleSecondary),
        link = SpanStyle(color = colors.textLinkExternal, textDecoration = TextDecoration.Underline),
        dim = SpanStyle(color = colors.textSecondary),
        checkbox = SpanStyle(fontSize = 1.5.em, color = colors.iconAccentPrimary),
    )
}

/** The formatting buttons above the keyboard, scrolling sideways on narrow phones, after the attach button. */
@Composable
private fun FormattingToolbar(enabled: Boolean, canAttach: Boolean, onAction: (FormatAction) -> Unit, onAttach: (uri: String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
    ) {
        AttachButton(enabled = canAttach, onPick = onAttach)
        FormatAction.entries.forEach { action ->
            IconButton(onClick = { onAction(action) }, enabled = enabled) {
                Icon(imageVector = action.icon(), contentDescription = stringResource(action.label()))
            }
        }
    }
}

/** 📎: a photo from the gallery (Android's photo picker, no permission needed) or any file. */
@Composable
private fun AttachButton(enabled: Boolean, onPick: (uri: String) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { onPick(it.toString()) } }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { onPick(it.toString()) } }
    Box {
        IconButton(onClick = { showMenu = true }, enabled = enabled) {
            Icon(imageVector = CompoundIcons.Attachment(), contentDescription = stringResource(R.string.a11y_notes_attach))
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.screen_notes_attach_photo)) },
                leadingIcon = { Icon(imageVector = CompoundIcons.Image(), contentDescription = null) },
                onClick = {
                    showMenu = false
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.screen_notes_attach_file)) },
                leadingIcon = { Icon(imageVector = CompoundIcons.Document(), contentDescription = null) },
                onClick = {
                    showMenu = false
                    pickFile.launch(arrayOf("*/*"))
                },
            )
        }
    }
}

@Composable
private fun FormatAction.icon(): ImageVector = when (this) {
    FormatAction.BOLD -> CompoundIcons.Bold()
    FormatAction.ITALIC -> CompoundIcons.Italic()
    FormatAction.STRIKETHROUGH -> CompoundIcons.Strikethrough()
    FormatAction.HEADING -> CompoundIcons.Section()
    FormatAction.BULLET_LIST -> CompoundIcons.ListBulleted()
    FormatAction.NUMBERED_LIST -> CompoundIcons.ListNumbered()
    FormatAction.CHECKLIST -> CompoundIcons.CheckCircle()
    FormatAction.QUOTE -> CompoundIcons.Quote()
    FormatAction.NOTE_LINK -> CompoundIcons.Link()
    FormatAction.WEB_LINK -> CompoundIcons.WebBrowser()
}

private fun FormatAction.label(): Int = when (this) {
    FormatAction.BOLD -> R.string.a11y_notes_format_bold
    FormatAction.ITALIC -> R.string.a11y_notes_format_italic
    FormatAction.STRIKETHROUGH -> R.string.a11y_notes_format_strikethrough
    FormatAction.HEADING -> R.string.a11y_notes_format_heading
    FormatAction.BULLET_LIST -> R.string.a11y_notes_format_bullets
    FormatAction.NUMBERED_LIST -> R.string.a11y_notes_format_numbers
    FormatAction.CHECKLIST -> R.string.a11y_notes_format_checklist
    FormatAction.QUOTE -> R.string.a11y_notes_format_quote
    FormatAction.NOTE_LINK -> R.string.a11y_notes_format_link
    FormatAction.WEB_LINK -> R.string.a11y_notes_format_web_link
}

@PreviewsDayNight
@Composable
internal fun NoteEditorViewPreview(@PreviewParameter(NoteEditorStatePreviewParam::class) state: NoteEditorState) = ElementPreview {
    NoteEditorView(state = state)
}
