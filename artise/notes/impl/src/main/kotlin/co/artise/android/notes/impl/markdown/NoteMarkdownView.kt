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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
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
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Text
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
) {
    val document = remember(markdown) { NoteMarkdownParser.parse(markdown) }
    val actions = NoteActions(onLinkClick, onTaskToggle)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Blocks(parent = document, actions = actions)
    }
}

/** What taps in a note do: follow a link, or tick a checklist item (`null`: checklists are read-only). */
private data class NoteActions(
    val onLinkClick: (NoteLink) -> Unit,
    val onTaskToggle: ((Int) -> Unit)?,
)

@Composable
private fun Blocks(parent: Node, actions: NoteActions) {
    parent.children().forEach { Block(it, actions) }
}

@Composable
private fun Block(node: Node, actions: NoteActions) {
    val onLinkClick = actions.onLinkClick
    when (node) {
        is Heading -> InlineText(node, headingStyle(node.level), onLinkClick)
        is Paragraph -> InlineText(node, ElementTheme.typography.fontBodyLgRegular, onLinkClick)
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
        is HtmlBlock -> Text(node.literal.trimEnd(), style = ElementTheme.typography.fontBodyLgRegular)
        // Anything else the parser may produce: show its text rather than nothing.
        else -> InlineText(node, ElementTheme.typography.fontBodyLgRegular, onLinkClick)
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle = when (level) {
    1 -> ElementTheme.typography.fontHeadingLgBold
    2 -> ElementTheme.typography.fontHeadingMdBold
    3 -> ElementTheme.typography.fontHeadingSmMedium
    else -> ElementTheme.typography.fontBodyLgMedium
}

@Composable
private fun ListBlock(list: Node, ordered: Boolean, start: Int, actions: NoteActions) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        list.children().filterIsInstance<ListItem>().forEachIndexed { index, item ->
            // The checkbox marker sits at the start of the item, or of its first paragraph.
            val task = item.firstChild as? TaskListItemMarker ?: item.firstChild?.firstChild as? TaskListItemMarker
            val line = item.sourceSpans.firstOrNull()?.lineIndex
            val onTaskToggle = actions.onTaskToggle
            Row {
                if (task != null && line != null && onTaskToggle != null) {
                    Checkbox(checked = task.isChecked, onToggle = { onTaskToggle(line) })
                } else {
                    val marker = when {
                        task != null -> if (task.isChecked) "☑" else "☐"
                        ordered -> "${start + index}."
                        else -> "•"
                    }
                    Text(
                        text = marker,
                        style = ElementTheme.typography.fontBodyLgRegular,
                        color = ElementTheme.colors.textSecondary,
                        modifier = Modifier.width(28.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Blocks(item, actions)
                }
            }
        }
    }
}

/** A tappable checkbox for a checklist item, big enough to hit with a thumb and announced as a checkbox. */
@Composable
private fun Checkbox(checked: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 28.dp, height = 28.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        contentAlignment = Alignment.TopStart,
    ) {
        Text(
            text = if (checked) "☑" else "☐",
            style = ElementTheme.typography.fontBodyLgRegular,
            color = if (checked) ElementTheme.colors.textSecondary else ElementTheme.colors.textPrimary,
        )
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
            val style = if (first) ElementTheme.typography.fontBodyLgMedium else ElementTheme.typography.fontBodyLgRegular
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

@Composable
private fun InlineText(node: Node, style: TextStyle, onLinkClick: (NoteLink) -> Unit) {
    val linkColor = ElementTheme.colors.textLinkExternal
    val codeBackground = ElementTheme.colors.bgSubtleSecondary
    val text = remember(node, linkColor, codeBackground) {
        buildAnnotatedString { appendInlines(node, onLinkClick, linkColor, codeBackground) }
    }
    if (text.isNotEmpty()) Text(text = text, style = style, color = ElementTheme.colors.textPrimary)
}

private fun AnnotatedString.Builder.appendInlines(
    parent: Node,
    onLinkClick: (NoteLink) -> Unit,
    linkColor: androidx.compose.ui.graphics.Color,
    codeBackground: androidx.compose.ui.graphics.Color,
) {
    parent.children().forEach { node ->
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
    val link = WikiLinkRewriter.targetOf(destination)?.let { NoteLink.Note(it) } ?: NoteLink.Web(destination)
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
