/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import co.artise.android.notes.api.NotesRepository
import co.artise.android.notes.impl.markdown.ChecklistToggle
import co.artise.android.notes.impl.ui.folder.NotesFolderEntries
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

fun interface NoteEditorNavigator {
    /** The editor is done: saved, discarded, or left without changes. */
    fun onDone()
}

/**
 * Edits a note's Markdown with live preview. Saving stores it on the phone at once and sends it in the background.
 *
 * With [resolveEditId], it combines the two versions of a conflicted edit: this phone's text, a line, then the
 * other person's. Saving that becomes the chosen version.
 */
@AssistedInject
class NoteEditorPresenter(
    @Assisted private val roomId: RoomId,
    @Assisted private val path: String,
    @Assisted private val resolveEditId: Long?,
    @Assisted private val navigator: NoteEditorNavigator,
    private val repository: NotesRepository,
) : Presenter<NoteEditorState> {
    @AssistedFactory
    fun interface Factory {
        fun create(roomId: RoomId, path: String, resolveEditId: Long?, navigator: NoteEditorNavigator): NoteEditorPresenter
    }

    @Composable
    override fun present(): NoteEditorState {
        val scope = rememberCoroutineScope()
        var value by remember { mutableStateOf(TextFieldValue()) }
        var isLoaded by remember { mutableStateOf(false) }
        var original by remember { mutableStateOf("") }
        var notePaths by remember { mutableStateOf<ImmutableList<String>>(persistentListOf()) }
        var linkEdit by remember { mutableStateOf<LinkEditState?>(null) }
        var showSaveChangesDialog by remember { mutableStateOf(false) }
        var isSaving by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            val initial = if (resolveEditId != null) {
                val edit = repository.edits(roomId).firstOrNull { it.id == resolveEditId }
                listOfNotNull(edit?.content, edit?.serverCopy?.content).joinToString(separator = CONFLICT_SEPARATOR)
            } else {
                repository.file(roomId, path)?.content.orEmpty()
            }
            value = TextFieldValue(initial, TextRange(initial.length))
            original = initial
            notePaths = repository.files(roomId).filter { it.isNote }.map { it.path }.toImmutableList()
            isLoaded = true
        }

        val text = value.text
        // A combined text is unsaved from the start: the choice isn't made until it's saved.
        val hasUnsavedChanges = isLoaded && (resolveEditId != null || text != original)
        val openLink = if (value.selection.collapsed) WikiLinkSuggestions.openLinkAt(text, value.selection.start) else null
        val suggestions = openLink?.let { WikiLinkSuggestions.suggestionsFor(it.query, notePaths, currentPath = path).toImmutableList() }
            ?: persistentListOf()

        fun onValueChange(new: TextFieldValue) {
            val old = value
            // Enter on a list line continues the list, or ends it on an empty item.
            continuedList(old, new)?.let {
                value = it
                return
            }
            // A tap on a formatted line: its checkbox ticks, its link opens the link dialog, anything else places the cursor.
            if (new.text == old.text && new.selection.collapsed && new.selection != old.selection) {
                val wasFormatted = LivePreview.lineOf(new.text, new.selection.start) !in LivePreview.rawLines(old.text, old.selection.start, old.selection.end)
                val hit = if (wasFormatted) LivePreview.hitAt(new.text, new.selection.start) else null
                when (hit) {
                    is LivePreviewHit.Checkbox -> {
                        // "[ ]" and "[x]" are the same length, so the cursor stays where it was.
                        ChecklistToggle.toggle(old.text, hit.lineIndex)?.let { value = old.copy(text = it) }
                        return
                    }
                    is LivePreviewHit.Link -> {
                        linkEdit = LinkEditState(hit.start, hit.end, hit.link)
                        return
                    }
                    null -> Unit
                }
            }
            value = new
        }

        fun replaceLink(edit: LinkEditState, replacement: String) {
            val newText = text.substring(0, edit.start) + replacement + text.substring(edit.end)
            val shift = replacement.length - (edit.end - edit.start)
            fun moved(offset: Int) = if (offset >= edit.end) offset + shift else offset.coerceAtMost(edit.start + replacement.length)
            value = TextFieldValue(newText, TextRange(moved(value.selection.start), moved(value.selection.end)))
            linkEdit = null
        }

        fun save() {
            if (isSaving) return
            isSaving = true
            scope.launch {
                if (resolveEditId != null) {
                    repository.resolveConflict(resolveEditId, value.text)
                } else {
                    repository.editNote(roomId, path, value.text)
                }
                repository.syncInBackground(roomId)
                navigator.onDone()
            }
        }

        return NoteEditorState(
            title = NotesFolderEntries.noteName(path),
            value = value,
            rawLines = LivePreview.rawLines(text, value.selection.start, value.selection.end),
            isLoading = !isLoaded,
            isResolvingConflict = resolveEditId != null,
            hasUnsavedChanges = hasUnsavedChanges,
            suggestions = suggestions,
            notePaths = notePaths,
            linkEdit = linkEdit,
            showSaveChangesDialog = showSaveChangesDialog,
            eventSink = { event ->
                when (event) {
                    is NoteEditorEvent.ValueChanged -> onValueChange(event.value)
                    is NoteEditorEvent.SelectSuggestion -> {
                        val cursor = value.selection.start
                        val link = WikiLinkSuggestions.openLinkAt(text, cursor) ?: return@NoteEditorState
                        val (newText, newCursor) = WikiLinkSuggestions.complete(text, link, cursor, event.suggestion)
                        value = TextFieldValue(newText, TextRange(newCursor))
                    }
                    is NoteEditorEvent.Format -> {
                        val edited = MarkdownFormatting.apply(event.action, text, value.selection.start, value.selection.end)
                        value = TextFieldValue(edited.text, TextRange(edited.start, edited.end))
                    }
                    is NoteEditorEvent.SaveLink -> linkEdit?.let { replaceLink(it, event.link.toMarkdown()) }
                    NoteEditorEvent.RemoveLink -> linkEdit?.let { replaceLink(it, it.link.label()) }
                    NoteEditorEvent.DismissLinkEdit -> linkEdit = null
                    NoteEditorEvent.Save -> if (hasUnsavedChanges) save() else navigator.onDone()
                    NoteEditorEvent.Back -> if (hasUnsavedChanges) showSaveChangesDialog = true else navigator.onDone()
                    NoteEditorEvent.DiscardChanges -> {
                        showSaveChangesDialog = false
                        navigator.onDone()
                    }
                    NoteEditorEvent.DismissSaveChangesDialog -> showSaveChangesDialog = false
                }
            },
        )
    }

    /** [new] is [old] plus one typed newline: apply list continuation, or `null` to keep [new] as it is. */
    private fun continuedList(old: TextFieldValue, new: TextFieldValue): TextFieldValue? {
        if (!new.selection.collapsed || new.text.length != old.text.length + 1) return null
        val cursor = new.selection.start
        if (cursor == 0 || new.text[cursor - 1] != '\n' || new.text.removeRange(cursor - 1, cursor) != old.text) return null
        val result = MarkdownFormatting.continueListOnEnter(old.text, cursor - 1) ?: return null
        return TextFieldValue(result.text, TextRange(result.start, result.end))
    }

    companion object {
        /** Between this phone's version and the other person's when combining: a plain line, clear in any language. */
        const val CONFLICT_SEPARATOR = "\n\n———\n\n"
    }
}
