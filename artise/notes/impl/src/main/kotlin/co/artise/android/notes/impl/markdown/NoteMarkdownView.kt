/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Checkbox
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Text
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.ThematicBreak
import java.net.URLDecoder
import org.commonmark.node.Text as TextNode

/**
 * Shows a note: headings, paragraphs, lists and checklists, quotes, code, tables and links.
 * Links to other notes and web links are passed to [onLinkClick]; nothing opens by itself.
 */
@Composable
fun NoteMarkdownView(
    markdown: String,
    onLinkClick: (NoteLink) -> Unit,
    modifier: Modifier = Modifier,
    onTaskToggle: ((lineIndex: Int) -> Unit)? = null,
    embed: (@Composable (target: String) -> Unit)? = null,
) {
    val document = remember(markdown) { NoteMarkdownParser.parse(markdown) }
    val actions = NoteActions(onLinkClick, onTaskToggle, embed)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Blocks(parent = document, actions = actions)
    }
}

/** What taps in a note do: follow a link, or tick a checklist item (`null`: checklists are read-only). */
private data class NoteActions(
    val onLinkClick: (NoteLink) -> Unit,
    val onTaskToggle: ((Int) -> Unit)?,
    /** Draws an embedded photo or file (`![[photo.jpg]]` on its own line); `null` shows embeds as links. */
    val embed: (@Composable (target: String) -> Unit)?,
)

/** The photo or file an image destination points to: a `[[wiki]]` embed target, or a relative path. `null` for web images. */
internal fun embedTarget(destination: String): String? =
    WikiLinkRewriter.targetOf(destination)
        ?: destination.takeIf { "://" !in it }?.let { URLDecoder.decode(it, Charsets.UTF_8.name()).removePrefix("./") }

@Composable
private fun Blocks(parent: Node, actions: NoteActions) {
    parent.children().forEach { Block(it, actions) }
}

@Composable
private fun Block(node: Node, actions: NoteActions) {
    val onLinkClick = actions.onLinkClick
    when (node) {
        is Heading -> InlineText(node, headingStyle(node.level), onLinkClick)
        is Paragraph -> ParagraphWithEmbeds(node, actions)
        is BulletList -> ListBlock(node, ordered = false, start = 1, actions = actions)
        is OrderedList -> ListBlock(node, ordered = true, start = node.markerStartNumber ?: 1, actions = actions)
        is BlockQuote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Column(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(ElementTheme.colors.borderInteractiveSecondary)
            ) {}
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Blocks(node, actions)
            }
        }
        is FencedCodeBlock -> CodeBlock(node.literal)
        is IndentedCodeBlock -> CodeBlock(node.literal)
        is ThematicBreak -> HorizontalDivider()
        is TableBlock -> TableView(node, onLinkClick)
        is HtmlBlock -> Text(node.literal.trimEnd(), style = bodyStyle())
        // Anything else the parser may produce: show its text rather than nothing.
        else -> InlineText(node, bodyStyle(), onLinkClick)
    }
}

/** Body text with generous line spacing: notes are read on phones, often by older family members. */
@Composable
private fun bodyStyle(): TextStyle = ElementTheme.typography.fontBodyLgRegular.copy(lineHeight = BODY_LINE_HEIGHT)

@Composable
private fun headingStyle(level: Int): TextStyle = when (level) {
    1 -> ElementTheme.typography.fontHeadingLgBold
    2 -> ElementTheme.typography.fontHeadingMdBold
    3 -> ElementTheme.typography.fontHeadingSmMedium
    else -> ElementTheme.typography.fontBodyLgMedium
}.copy(lineHeight = HEADING_LINE_HEIGHT)

private val BODY_LINE_HEIGHT = 1.55.em
private val HEADING_LINE_HEIGHT = 1.3.em

@Composable
private fun ListBlock(list: Node, ordered: Boolean, start: Int, actions: NoteActions) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        list.children().filterIsInstance<ListItem>().forEachIndexed { index, item ->
            // The checkbox marker sits at the start of the item, or of its first paragraph.
            val task = item.firstChild as? TaskListItemMarker ?: item.firstChild?.firstChild as? TaskListItemMarker
            val line = item.sourceSpans.firstOrNull()?.lineIndex
            val onTaskToggle = actions.onTaskToggle
            Row(verticalAlignment = if (task != null) Alignment.CenterVertically else Alignment.Top) {
                if (task != null) {
                    // A full-size checkbox (48dp to tap); read-only where the note can't be edited.
                    Checkbox(
                        checked = task.isChecked,
                        onCheckedChange = if (line != null && onTaskToggle != null) {
                            { onTaskToggle(line) }
                        } else {
                            null
                        },
                    )
                } else {
                    val marker = if (ordered) "${start + index}." else "•"
                    Text(
                        text = marker,
                        style = bodyStyle(),
                        color = ElementTheme.colors.textSecondary,
                        modifier = Modifier.width(28.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Blocks(item, actions)
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(literal: String) {
    Text(
        text = literal.trimEnd(),
        style = ElementTheme.typography.fontBodyMdRegular.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier
            .fillMaxWidth()
            .background(ElementTheme.colors.bgSubtleSecondary, RoundedCornerShape(8.dp))
            .horizontalScroll(rememberScrollState())
            .padding(12.dp),
    )
}

@Composable
private fun TableView(table: TableBlock, onLinkClick: (NoteLink) -> Unit) {
    // Phones are narrow: each row reads as "cell · cell · cell", the header row in bold.
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var first = true
        table.descendants().filterIsInstance<TableRow>().forEach { row ->
            val style = if (first) ElementTheme.typography.fontBodyLgMedium else bodyStyle()
            val linkColor = ElementTheme.colors.textLinkExternal
            val codeBackground = ElementTheme.colors.bgSubtleSecondary
            val text = buildAnnotatedString {
                row.children().filterIsInstance<TableCell>().forEachIndexed { index, cell ->
                    if (index > 0) append("  ·  ")
                    appendInlines(cell, onLinkClick, linkColor, codeBackground)
                }
            }
            Text(text, style = style)
            first = false
        }
    }
}

/** A paragraph split around its embeds: runs of text, and the photos or files between them. */
@Immutable
private sealed interface ParagraphPiece {
    data class Text(val nodes: ImmutableList<Node>) : ParagraphPiece

    data class Embed(val target: String) : ParagraphPiece
}

/**
 * A paragraph whose embedded photos and files ("![[photo.jpg]]") show in place, even when they share the paragraph
 * with text, as notes from Ari or Obsidian often do ("Hoy:\n![[foto.jpg]]"). The text around them flows as usual.
 */
@Composable
private fun ParagraphWithEmbeds(paragraph: Paragraph, actions: NoteActions) {
    val embed = actions.embed
    val pieces = remember(paragraph) { piecesOf(paragraph) }
    if (embed == null || pieces.none { it is ParagraphPiece.Embed }) {
        InlineText(paragraph.children().toList().toImmutableList(), bodyStyle(), actions.onLinkClick)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pieces.forEach { piece ->
            when (piece) {
                is ParagraphPiece.Embed -> embed(piece.target)
                is ParagraphPiece.Text -> InlineText(piece.nodes, bodyStyle(), actions.onLinkClick)
            }
        }
    }
}

private fun piecesOf(paragraph: Paragraph): ImmutableList<ParagraphPiece> {
    val pieces = mutableListOf<ParagraphPiece>()
    val run = mutableListOf<Node>()
    fun endRun() {
        if (run.isNotEmpty()) pieces.add(ParagraphPiece.Text(run.toImmutableList()))
        run.clear()
    }
    for (child in paragraph.children()) {
        val target = (child as? Image)?.destination?.let(::embedTarget)
        if (target != null) {
            endRun()
            pieces.add(ParagraphPiece.Embed(target))
        } else {
            run.add(child)
        }
    }
    endRun()
    return pieces.toImmutableList()
}

@Composable
private fun InlineText(node: Node, style: TextStyle, onLinkClick: (NoteLink) -> Unit) =
    InlineText(node.children().toList().toImmutableList(), style, onLinkClick)

@Composable
private fun InlineText(nodes: ImmutableList<Node>, style: TextStyle, onLinkClick: (NoteLink) -> Unit) {
    val linkColor = ElementTheme.colors.textLinkExternal
    val codeBackground = ElementTheme.colors.bgSubtleSecondary
    val text = remember(nodes, linkColor, codeBackground) {
        buildAnnotatedString { appendInlines(nodes.asSequence(), onLinkClick, linkColor, codeBackground) }.trimmed()
    }
    if (text.isNotEmpty()) Text(text = text, style = style, color = ElementTheme.colors.textPrimary)
}

/** Without the spaces and line breaks an embed leaves at the start or end of a text run. */
private fun AnnotatedString.trimmed(): AnnotatedString {
    val start = text.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
    val end = text.indexOfLast { !it.isWhitespace() } + 1
    return if (start == 0 && end == text.length) this else subSequence(start, end.coerceAtLeast(start))
}

private fun AnnotatedString.Builder.appendInlines(
    parent: Node,
    onLinkClick: (NoteLink) -> Unit,
    linkColor: androidx.compose.ui.graphics.Color,
    codeBackground: androidx.compose.ui.graphics.Color,
) = appendInlines(parent.children(), onLinkClick, linkColor, codeBackground)

private fun AnnotatedString.Builder.appendInlines(
    nodes: Sequence<Node>,
    onLinkClick: (NoteLink) -> Unit,
    linkColor: androidx.compose.ui.graphics.Color,
    codeBackground: androidx.compose.ui.graphics.Color,
) {
    nodes.forEach { node ->
        when (node) {
            is TextNode -> append(node.literal)
            is SoftLineBreak -> append(" ")
            is HardLineBreak -> append("\n")
            is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInlines(node, onLinkClick, linkColor, codeBackground) }
            is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInlines(node, onLinkClick, linkColor, codeBackground) }
            is Strikethrough -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                appendInlines(node, onLinkClick, linkColor, codeBackground)
            }
            is Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) { append(node.literal) }
            is Link -> appendLink(node.destination, onLinkClick, linkColor) { appendInlines(node, onLinkClick, linkColor, codeBackground) }
            // Images and `![[embeds]]` show as a link to the file; opening files comes later.
            is Image -> appendLink(node.destination, onLinkClick, linkColor) {
                append("🖼 ")
                appendInlines(node, onLinkClick, linkColor, codeBackground)
            }
            is HtmlInline -> append(node.literal)
            // A checklist marker is drawn by the list, not as text.
            is TaskListItemMarker -> Unit
            else -> appendInlines(node, onLinkClick, linkColor, codeBackground)
        }
    }
}

private fun AnnotatedString.Builder.appendLink(
    destination: String,
    onLinkClick: (NoteLink) -> Unit,
    linkColor: androidx.compose.ui.graphics.Color,
    content: AnnotatedString.Builder.() -> Unit,
) {
    val link = WikiLinkRewriter.targetOf(destination)?.let { NoteLink.Note(it) }
        // A relative path ("Fotos/luna.jpg") is a file in the chat, not a web address.
        ?: embedTarget(destination)?.takeIf { !destination.startsWith("www.") && !destination.startsWith("mailto:") }?.let { NoteLink.Note(it) }
        ?: NoteLink.Web(destination)
    val styles = TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    withLink(LinkAnnotation.Clickable(tag = destination, styles = styles) { onLinkClick(link) }) { content() }
}

private fun Node.children(): Sequence<Node> = generateSequence(firstChild) { it.next }

private fun Node.descendants(): Sequence<Node> = children().flatMap { sequenceOf(it) + it.descendants() }

@PreviewsDayNight
@Composable
internal fun NoteMarkdownViewPreview() = ElementPreview {
    NoteMarkdownView(
        markdown = """
            # Mole poblano
            Receta de la abuela. Ver también [[Súper|la lista del súper]] y [la página](https://artise.co).

            ## Ingredientes
            - [x] chiles **mulato** y *ancho*
            - [ ] chocolate
            - ajonjolí

            > Tostar sin quemar.

            | Paso | Tiempo |
            | --- | --- |
            | Freír | 10 min |
        """.trimIndent(),
        onLinkClick = {},
        modifier = Modifier.padding(16.dp),
    )
}
