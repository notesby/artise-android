/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.document

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import co.artise.android.notes.api.NoteAttachment
import co.artise.android.notes.impl.R
import co.artise.android.notes.impl.ui.note.openDownloadedFile
import co.artise.android.notes.impl.ui.note.photoRequest
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.ui.strings.CommonStrings
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesDocumentView(
    state: NotesDocumentState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    state.openFile?.let { request ->
        LaunchedEffect(request) { state.eventSink(NotesDocumentEvent.FileOpenHandled(opened = context.openDownloadedFile(request))) }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBackClick) },
                actions = {
                    if (state.file != null) {
                        IconButton(onClick = { state.eventSink(NotesDocumentEvent.OpenWith) }) {
                            Icon(imageVector = CompoundIcons.PopOut(), contentDescription = stringResource(CommonStrings.action_open_with))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            val file = state.file
            when {
                file != null -> DocumentContent(file, state.kind, state.title)
                state.isLoading -> CircularProgressIndicator()
                state.problem == DocumentProblem.OFFLINE || state.problem == DocumentProblem.FAILED -> Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(
                            if (state.problem == DocumentProblem.OFFLINE) R.string.screen_notes_document_offline else R.string.screen_notes_document_failed,
                        ),
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(
                        text = stringResource(CommonStrings.action_retry),
                        onClick = { state.eventSink(NotesDocumentEvent.Retry) },
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
    if (state.problem == DocumentProblem.NO_APP) {
        ErrorDialog(
            content = stringResource(R.string.screen_notes_attachment_no_app),
            onSubmit = { state.eventSink(NotesDocumentEvent.DismissProblem) },
        )
    }
}

@Composable
private fun DocumentContent(file: String, kind: NoteAttachment.Kind, name: String) {
    when (kind) {
        NoteAttachment.Kind.PDF -> {
            val document = rememberPdfDocument(file)
            if (document != null) {
                PdfPagesView(document)
            } else {
                Text(
                    text = stringResource(R.string.screen_notes_document_failed),
                    color = ElementTheme.colors.textSecondary,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        NoteAttachment.Kind.IMAGE -> ZoomableAsyncImage(
            model = photoRequest(file),
            contentDescription = name,
            modifier = Modifier.fillMaxSize(),
        )
        NoteAttachment.Kind.NOTE,
        NoteAttachment.Kind.OTHER -> Icon(
            imageVector = CompoundIcons.Attachment(),
            contentDescription = null,
            tint = ElementTheme.colors.iconSecondary,
            modifier = Modifier.size(48.dp),
        )
    }
}

@PreviewsDayNight
@Composable
internal fun NotesDocumentViewPreview(@PreviewParameter(NotesDocumentStatePreviewParam::class) state: NotesDocumentState) = ElementPreview {
    NotesDocumentView(state = state, onBackClick = {})
}
