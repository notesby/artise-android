/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.artise.android.notes.api.DocumentCard
import co.artise.android.notes.api.DocumentCardRenderer
import co.artise.android.notes.api.NoteAttachment
import co.artise.android.notes.api.NotesPreview
import co.artise.android.notes.api.NotesRepository
import co.artise.android.notes.impl.R
import co.artise.android.notes.impl.markdown.NoteFrontmatter
import co.artise.android.notes.impl.ui.editor.LivePreview
import co.artise.android.notes.impl.ui.editor.LivePreviewStyles
import co.artise.android.notes.impl.ui.editor.livePreviewStyles
import co.artise.android.notes.impl.ui.note.photoRequest
import coil3.compose.AsyncImage
import dev.zacsweers.metro.ContributesBinding
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.RoomId

/**
 * The card under Ari's message for a document it made or changed: the document's preview (a picture of a PDF's,
 * photo's or Office file's first page, a note's first lines formatted, a CSV's first rows), its name, and whether it is
 * new, changed or a draft. The preview comes from the Notes API, kept on the phone per version of the file.
 */
@ContributesBinding(SessionScope::class)
class DefaultDocumentCardRenderer(
    private val repository: NotesRepository,
) : DocumentCardRenderer {
    @Composable
    override fun Render(roomId: RoomId, card: DocumentCard, onClick: () -> Unit, modifier: Modifier) {
        val dark = !ElementTheme.isLightTheme
        // A chart or dashboard changes while its version doesn't (it may read records): the kept picture at once, then
        // asked again (a 304 when it's the same). Its card's version is no key for it.
        val isDrawn = card.kind.isDrawn
        val preview by produceState<NotesPreview?>(initialValue = null, roomId, card.path, card.version.takeUnless { isDrawn }, dark) {
            if (isDrawn) repository.cachedPreview(roomId, card.path, PREVIEW_SIZE, dark)?.let { value = it }
            value = repository.preview(roomId, card.path, PREVIEW_SIZE, dark)
        }
        // A chart's picture message only knows the picture's name: the chart's own title once the phone has it.
        val title by produceState(card.title, roomId, card.path) {
            if (card.kind == NoteAttachment.Kind.CHART) repository.file(roomId, card.path)?.title?.takeIf { it.isNotBlank() }?.let { value = it }
        }
        DocumentCardView(card = card.copy(title = title), preview = preview, onClick = onClick, modifier = modifier)
    }
}

@Composable
internal fun DocumentCardView(card: DocumentCard, preview: NotesPreview?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val openLabel = stringResource(R.string.a11y_notes_card_open, card.title)
    Column(
        modifier = modifier
            .clip(shape)
            .border(1.dp, ElementTheme.colors.borderInteractiveSecondary, shape)
            .background(ElementTheme.colors.bgCanvasDefault)
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PREVIEW_HEIGHT)
                .background(ElementTheme.colors.bgSubtleSecondary),
            contentAlignment = Alignment.Center,
        ) {
            CardPreview(card, preview)
        }
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(imageVector = iconOf(card.kind), contentDescription = null, tint = ElementTheme.colors.iconSecondary, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.title,
                    style = ElementTheme.typography.fontBodyMdMedium,
                    color = ElementTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                statusText(card)?.let { status ->
                    Text(text = status, style = ElementTheme.typography.fontBodySmRegular, color = ElementTheme.colors.textSecondary, maxLines = 1)
                }
            }
            if (card.isDraft) DraftBadge()
        }
    }
}

@Composable
private fun CardPreview(card: DocumentCard, preview: NotesPreview?) {
    when (preview) {
        is NotesPreview.Image -> AsyncImage(
            model = photoRequest(preview.file.path),
            contentDescription = null,
            // A chart whole (its axis is at the bottom); a page from its top.
            contentScale = if (card.kind == NoteAttachment.Kind.CHART) ContentScale.Fit else ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize(),
        )
        is NotesPreview.Text -> if (preview.text.isNotBlank()) {
            TextPreview(card.kind, preview.text)
        } else {
            KindIcon(card.kind)
        }
        // Still coming, or no preview for this kind of file.
        NotesPreview.None, null -> KindIcon(card.kind)
    }
}

/** A note's first lines formatted as the reading view shows them; a CSV's first rows with its columns lined up. */
@Composable
private fun TextPreview(kind: NoteAttachment.Kind, text: String) {
    val isTable = kind == NoteAttachment.Kind.TABLE
    val styles = livePreviewStyles()
    val shown = remember(text, isTable, styles) { if (isTable) AnnotatedString(text) else formatted(text, styles) }
    Text(
        text = shown,
        style = if (isTable) {
            ElementTheme.typography.fontBodySmRegular.copy(fontFamily = FontFamily.Monospace)
        } else {
            ElementTheme.typography.fontBodyMdRegular
        },
        color = ElementTheme.colors.textPrimary,
        softWrap = !isTable,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
    )
}

/** The note without its `---` header and leading blank lines, its Markdown marks hidden. */
internal fun formatted(markdown: String, styles: LivePreviewStyles): AnnotatedString {
    val body = NoteFrontmatter.blank(markdown).trimStart('\n', ' ')
    return LivePreview.transform(body, IntRange.EMPTY, styles).text
}

@Composable
private fun KindIcon(kind: NoteAttachment.Kind) {
    Icon(imageVector = iconOf(kind), contentDescription = null, tint = ElementTheme.colors.iconTertiary, modifier = Modifier.size(40.dp))
}

@Composable
private fun DraftBadge() {
    Text(
        text = stringResource(R.string.screen_notes_card_draft),
        style = ElementTheme.typography.fontBodyXsMedium,
        color = ElementTheme.colors.textOnSolidPrimary,
        modifier = Modifier
            .background(ElementTheme.colors.bgActionPrimaryRest, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun statusText(card: DocumentCard): String? = when (card.change) {
    DocumentCard.Change.CREATED -> stringResource(R.string.screen_notes_card_created)
    DocumentCard.Change.CHANGED -> stringResource(R.string.screen_notes_card_changed)
    null -> null
}

/** Charts and dashboards: drawn by the server for a light or dark screen, and changing with the records they read. */
internal val NoteAttachment.Kind.isDrawn: Boolean get() = this == NoteAttachment.Kind.CHART || this == NoteAttachment.Kind.DASHBOARD

@Composable
internal fun iconOf(kind: NoteAttachment.Kind): ImageVector = when (kind) {
    NoteAttachment.Kind.NOTE -> CompoundIcons.Document()
    NoteAttachment.Kind.IMAGE -> CompoundIcons.Image()
    NoteAttachment.Kind.TABLE -> CompoundIcons.Grid()
    NoteAttachment.Kind.CHART -> CompoundIcons.Chart()
    NoteAttachment.Kind.DASHBOARD -> CompoundIcons.Extensions()
    NoteAttachment.Kind.PDF,
    NoteAttachment.Kind.OFFICE,
    NoteAttachment.Kind.OTHER -> CompoundIcons.Attachment()
}

@PreviewsDayNight
@Composable
internal fun DocumentCardViewPreview() = ElementPreview {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp).widthIn(max = 320.dp)) {
        DocumentCardView(
            card = DocumentCard("Contratos/Contrato Ana.md", "3f2a", "Contrato Ana", DocumentCard.Change.CREATED, isDraft = true),
            preview = NotesPreview.Text(
                "---\ntitle: Contrato Ana\n---\n# Contrato de servicios\nEntre **Ana López** y *Tienda Prueba*.\n- Pago mensual\n- [ ] Firmar"
            ),
            onClick = {},
        )
        DocumentCardView(
            card = DocumentCard("gastos.csv", null, "gastos.csv", DocumentCard.Change.CHANGED, isDraft = false),
            preview = NotesPreview.Text("fecha,concepto,monto\n2026-10-01,Renta,8500\n2026-10-02,Luz,640"),
            onClick = {},
        )
        DocumentCardView(
            card = DocumentCard("charts/ventas.chart", null, "Ventas por mes", DocumentCard.Change.CREATED, isDraft = false, serverKind = "chart"),
            preview = null,
            onClick = {},
        )
        DocumentCardView(
            card = DocumentCard("docs/oferta.docx", null, "oferta.docx", change = null, isDraft = false),
            preview = NotesPreview.None,
            onClick = {},
        )
    }
}

private const val PREVIEW_SIZE = 512
private val PREVIEW_HEIGHT = 150.dp
