/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.document

import android.graphics.RectF
import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import co.artise.android.notes.api.NoteAttachment
import co.artise.android.notes.impl.R
import co.artise.android.notes.impl.ui.find.FindBar
import co.artise.android.notes.impl.ui.find.FindUnavailable
import co.artise.android.notes.impl.ui.find.TextFinder
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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
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
                    if (state.canFind && state.find == null) {
                        IconButton(onClick = { state.eventSink(NotesDocumentEvent.OpenFind) }) {
                            Icon(imageVector = CompoundIcons.Search(), contentDescription = stringResource(R.string.a11y_notes_find))
                        }
                    }
                    if (state.file != null) {
                        IconButton(onClick = { state.eventSink(NotesDocumentEvent.OpenWith) }) {
                            Icon(imageVector = CompoundIcons.PopOut(), contentDescription = stringResource(CommonStrings.action_open_with))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            state.find?.let { find -> DocumentFindBar(find, state.eventSink) }
            Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                DocumentBody(state)
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
private fun DocumentFindBar(find: DocumentFind, eventSink: (NotesDocumentEvent) -> Unit) {
    if (find.isUnavailable) {
        FindUnavailable(onClose = { eventSink(NotesDocumentEvent.CloseFind) })
        return
    }
    val match = find.currentMatch
    // Older phones can't place the match on the page: a little of the text around it stands in for the highlight.
    val snippet = match?.takeIf { Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM }?.let { TextFinder.snippet(find.pages[it.page], it) }
    FindBar(
        query = find.query,
        matchCount = find.matches.size,
        current = find.current,
        onQueryChange = { eventSink(NotesDocumentEvent.FindQueryChanged(it)) },
        onNext = { eventSink(NotesDocumentEvent.FindNext) },
        onPrevious = { eventSink(NotesDocumentEvent.FindPrevious) },
        onClose = { eventSink(NotesDocumentEvent.CloseFind) },
        snippet = snippet,
    )
}

@Composable
private fun DocumentBody(state: NotesDocumentState) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val file = state.file
            when {
                file != null -> DocumentContent(state, file)
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

@Composable
private fun DocumentContent(state: NotesDocumentState, file: String) {
    val pdfFile = state.pdfFile
    val tableText = state.tableText
    when {
        pdfFile != null -> {
            val document = rememberPdfDocument(pdfFile)
            if (document != null) {
                PdfPagesWithFind(document, state.find)
            } else {
                Text(
                    text = stringResource(R.string.screen_notes_document_failed),
                    color = ElementTheme.colors.textSecondary,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        state.kind == NoteAttachment.Kind.PDF -> Text(
            text = stringResource(R.string.screen_notes_document_failed),
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier.padding(24.dp),
        )
        state.kind == NoteAttachment.Kind.IMAGE -> ZoomableAsyncImage(
            model = photoRequest(file),
            contentDescription = state.title,
            modifier = Modifier.fillMaxSize(),
        )
        tableText != null -> TableTextView(tableText, state.find)
        // An Office file the server couldn't convert, or anything else: "Open with" at the top hands it to another app.
        else -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(
                imageVector = CompoundIcons.Attachment(),
                contentDescription = null,
                tint = ElementTheme.colors.iconSecondary,
                modifier = Modifier.size(48.dp),
            )
            if (state.kind == NoteAttachment.Kind.OFFICE) {
                Text(
                    text = stringResource(R.string.screen_notes_document_open_with_hint),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

/** A CSV as plain text in a fixed-width font, so its columns line up; scrolls both ways, to the current match too. */
@Composable
private fun TableTextView(text: String, find: DocumentFind?) {
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val matchColor = ElementTheme.colors.bgSubtlePrimary
    val currentColor = ElementTheme.colors.bgAccentRest.copy(alpha = 0.4f)
    val matches = find?.matches.orEmpty()
    val current = find?.currentMatch
    val shown = remember(text, matches, current) {
        buildAnnotatedString {
            append(text)
            matches.forEach { match ->
                addStyle(SpanStyle(background = if (match == current) currentColor else matchColor), match.start, match.end)
            }
        }
    }
    LaunchedEffect(current, layout) {
        val result = layout ?: return@LaunchedEffect
        val match = current ?: return@LaunchedEffect
        val line = result.getLineForOffset(match.start)
        vertical.animateScrollTo((result.getLineTop(line) - TABLE_SCROLL_MARGIN).toInt().coerceAtLeast(0))
        horizontal.animateScrollTo((result.getHorizontalPosition(match.start, usePrimaryDirection = true) - TABLE_SCROLL_MARGIN).toInt().coerceAtLeast(0))
    }
    Text(
        text = shown,
        style = ElementTheme.typography.fontBodySmRegular.copy(fontFamily = FontFamily.Monospace),
        softWrap = false,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(vertical)
            .horizontalScroll(horizontal)
            .padding(16.dp),
    )
}

private const val TABLE_SCROLL_MARGIN = 120f

@PreviewsDayNight
@Composable
internal fun NotesDocumentViewPreview(@PreviewParameter(NotesDocumentStatePreviewParam::class) state: NotesDocumentState) = ElementPreview {
    NotesDocumentView(state = state, onBackClick = {})
}

/** The pages, scrolled to the current match, with the matches on each page highlighted when the phone can place them. */
@Composable
private fun PdfPagesWithFind(document: PdfDocument, find: DocumentFind?) {
    val listState = rememberLazyListState()
    val current = find?.currentMatch
    LaunchedEffect(current) {
        if (current != null && current.page < document.pageCount) listState.animateScrollToItem(current.page)
    }
    PdfPagesView(document, listState = listState) { page ->
        val matchesHere = find?.matches?.filter { it.page == page }.orEmpty()
        if (matchesHere.isNotEmpty() && page < find!!.pages.size) {
            val text = find.pages[page]
            val needles = matchesHere.map { text.substring(it.start, it.end) }.toSet()
            val bounds by produceState<ImmutableList<RectF>>(initialValue = persistentListOf(), document, page, needles) {
                value = document.searchBounds(page, needles).toImmutableList()
            }
            // The renderer lists matches in reading order, as the server's text does: the nth here is the nth there.
            val currentIndexOnPage = matchesHere.indexOf(current)
            PdfHighlights(document, page, bounds, current = bounds.getOrNull(currentIndexOnPage))
        }
    }
}
