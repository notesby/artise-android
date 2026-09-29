/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.markdown

import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.Node
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser

/** Parses a note's Markdown, `[[wiki links]]` included, into a commonmark document. */
object NoteMarkdownParser {
    private val parser: Parser = Parser.builder()
        .extensions(listOf(TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create()))
        // Blocks know their source line, so a tapped checklist item can be ticked in the right line.
        // WikiLinkRewriter keeps lines as they are, so these line numbers match the note's own.
        .includeSourceSpans(IncludeSourceSpans.BLOCKS)
        .build()

    fun parse(markdown: String): Node = parser.parse(WikiLinkRewriter.rewrite(markdown))
}
