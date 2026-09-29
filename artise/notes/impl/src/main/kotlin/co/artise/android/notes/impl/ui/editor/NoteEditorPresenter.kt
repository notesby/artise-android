/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import co.artise.android.notes.api.NotesRepository
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
 * Edits a note's Markdown. Saving stores it on the phone at once and sends it in the background.
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
        val text = rememberTextFieldState()
        var isLoaded by remember { mutableStateOf(false) }
        var original by remember { mutableStateOf("") }
        var notePaths by remember { mutableStateOf(emptyList<String>()) }
        var suggestions by remember { mutableStateOf<ImmutableList<WikiLinkSuggestion>>(persistentListOf()) }
        var showSaveChangesDialog by remember { mutableStateOf(false) }
        var isSaving by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            val initial = if (resolveEditId != null) {
                val edit = repository.edits(roomId).firstOrNull { it.id == resolveEditId }
                listOfNotNull(edit?.content, edit?.serverCopy?.content).joinToString(separator = CONFLICT_SEPARATOR)
            } else {
                repository.file(roomId, path)?.content.orEmpty()
            }
            text.setTextAndPlaceCursorAtEnd(initial)
            original = initial
            isLoaded = true
            notePaths = repository.files(roomId).filter { it.isNote }.map { it.path }
        }
        LaunchedEffect(notePaths) {
            snapshotFlow { text.text.toString() to text.selection }.collect { (current, selection) ->
                val link = if (selection.collapsed) WikiLinkSuggestions.openLinkAt(current, selection.start) else null
                suggestions = link
                    ?.let { WikiLinkSuggestions.suggestionsFor(it.query, notePaths, currentPath = path).toImmutableList() }
                    ?: persistentListOf()
            }
        }

        // A combined text is unsaved from the start: the choice isn't made until it's saved.
        val hasUnsavedChanges = isLoaded && (resolveEditId != null || text.text.toString() != original)

        fun save() {
            if (isSaving) return
            isSaving = true
            scope.launch {
                val content = text.text.toString()
                if (resolveEditId != null) {
                    repository.resolveConflict(resolveEditId, content)
                } else {
                    repository.editNote(roomId, path, content)
                }
                repository.syncInBackground(roomId)
                navigator.onDone()
            }
        }

        return NoteEditorState(
            title = NotesFolderEntries.noteName(path),
            text = text,
            isLoading = !isLoaded,
            isResolvingConflict = resolveEditId != null,
            hasUnsavedChanges = hasUnsavedChanges,
            suggestions = suggestions,
            showSaveChangesDialog = showSaveChangesDialog,
            eventSink = { event ->
                when (event) {
                    is NoteEditorEvent.SelectSuggestion -> {
                        val current = text.text.toString()
                        val cursor = text.selection.start
                        val link = WikiLinkSuggestions.openLinkAt(current, cursor) ?: return@NoteEditorState
                        val (newText, newCursor) = WikiLinkSuggestions.complete(current, link, cursor, event.suggestion)
                        text.edit {
                            replace(0, length, newText)
                            selection = TextRange(newCursor)
                        }
                    }
                    is NoteEditorEvent.Format -> {
                        val edited = MarkdownFormatting.apply(event.action, text.text.toString(), text.selection.start, text.selection.end)
                        text.edit {
                            replace(0, length, edited.text)
                            selection = TextRange(edited.start, edited.end)
                        }
                    }
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

    companion object {
        /** Between this phone's version and the other person's when combining: a plain line, clear in any language. */
        const val CONFLICT_SEPARATOR = "\n\n———\n\n"
    }
}
