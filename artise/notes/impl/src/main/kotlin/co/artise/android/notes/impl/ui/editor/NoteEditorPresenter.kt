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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import co.artise.android.notes.api.NotesRepository
import co.artise.android.notes.impl.markdown.ChecklistToggle
import co.artise.android.notes.impl.ui.folder.NotesFolderEntries
import co.artise.android.notes.impl.ui.note.NoteEmbeds
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.extensions.mapCatchingExceptions
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.services.toolbox.api.systemclock.SystemClock
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
    private val clock: SystemClock,
    private val attachmentReader: AttachmentReader,
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
        val undoStack = remember { mutableStateListOf<TextFieldValue>() }
        val redoStack = remember { mutableStateListOf<TextFieldValue>() }
        var lastTypingAt by remember { mutableLongStateOf(0L) }
        var isAttaching by remember { mutableStateOf(false) }
        var attachError by remember { mutableStateOf<AttachError?>(null) }

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
        // A function, so event handlers see the text as it is now, not as it was when this state was built.
        fun hasChanges() = isLoaded && (resolveEditId != null || value.text != original)
        val hasUnsavedChanges = hasChanges()
        val openLink = if (value.selection.collapsed) WikiLinkSuggestions.openLinkAt(text, value.selection.start) else null
        val suggestions = openLink?.let { WikiLinkSuggestions.suggestionsFor(it.query, notePaths, currentPath = path).toImmutableList() }
            ?: persistentListOf()

        /**
         * Applies [new], remembering the text before it for undo. Typing without a pause is one undo step;
         * everything else (formatting, a tick, a link, a new list item) is a step of its own.
         */
        fun commit(new: TextFieldValue, isTyping: Boolean) {
            if (new.text != value.text) {
                val now = clock.epochMillis()
                if (!isTyping || now - lastTypingAt > TYPING_PAUSE_MILLIS || undoStack.isEmpty()) {
                    undoStack += value
                    if (undoStack.size > MAX_UNDO_STEPS) undoStack.removeAt(0)
                }
                lastTypingAt = if (isTyping) now else 0L
                redoStack.clear()
            }
            value = new
        }

        fun onValueChange(new: TextFieldValue) {
            val old = value
            // Enter on a list line continues the list, or ends it on an empty item.
            continuedList(old, new)?.let {
                commit(it, isTyping = false)
                return
            }
            // A tap on a formatted line: its checkbox ticks, its link opens the link dialog, anything else places the cursor.
            if (new.text == old.text && new.selection.collapsed && new.selection != old.selection) {
                val wasFormatted = LivePreview.lineOf(new.text, new.selection.start) !in LivePreview.rawLines(old.text, old.selection.start, old.selection.end)
                val hit = if (wasFormatted) LivePreview.hitAt(new.text, new.selection.start) else null
                when (hit) {
                    is LivePreviewHit.Checkbox -> {
                        // "[ ]" and "[x]" are the same length, so the cursor stays where it was.
                        ChecklistToggle.toggle(old.text, hit.lineIndex)?.let { commit(old.copy(text = it), isTyping = false) }
                        return
                    }
                    is LivePreviewHit.Link -> {
                        linkEdit = LinkEditState(hit.start, hit.end, hit.link)
                        return
                    }
                    null -> Unit
                }
            }
            commit(new, isTyping = true)
        }

        fun replaceLink(edit: LinkEditState, replacement: String) {
            val newText = value.text.substring(0, edit.start) + replacement + value.text.substring(edit.end)
            val shift = replacement.length - (edit.end - edit.start)
            fun moved(offset: Int) = if (offset >= edit.end) offset + shift else offset.coerceAtMost(edit.start + replacement.length)
            val selection = if (edit.isNew) {
                // A new link: the cursor goes right after it, ready to keep writing.
                TextRange(edit.start + replacement.length)
            } else {
                TextRange(moved(value.selection.start), moved(value.selection.end))
            }
            commit(TextFieldValue(newText, selection), isTyping = false)
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
            isAttaching = isAttaching,
            attachError = attachError,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            showSaveChangesDialog = showSaveChangesDialog,
            eventSink = { event ->
                when (event) {
                    is NoteEditorEvent.ValueChanged -> onValueChange(event.value)
                    is NoteEditorEvent.SelectSuggestion -> {
                        val cursor = value.selection.start
                        val link = WikiLinkSuggestions.openLinkAt(value.text, cursor) ?: return@NoteEditorState
                        val (newText, newCursor) = WikiLinkSuggestions.complete(value.text, link, cursor, event.suggestion)
                        commit(TextFieldValue(newText, TextRange(newCursor)), isTyping = false)
                    }
                    is NoteEditorEvent.Format -> if (event.action == FormatAction.WEB_LINK) {
                        val selection = value.selection
                        val start = minOf(selection.start, selection.end)
                        val end = maxOf(selection.start, selection.end)
                        linkEdit =
                            LinkEditState(start, end, EditableLink(isNote = false, target = "", shownText = value.text.substring(start, end)), isNew = true)
                    } else {
                        val edited = MarkdownFormatting.apply(event.action, value.text, value.selection.start, value.selection.end)
                        commit(TextFieldValue(edited.text, TextRange(edited.start, edited.end)), isTyping = false)
                    }
                    is NoteEditorEvent.Attach -> if (!isAttaching) {
                        isAttaching = true
                        scope.launch {
                            val uploaded = attachmentReader.read(event.uri).mapCatchingExceptions { picked ->
                                repository.addAttachment(roomId, picked.name, picked.bytes, picked.mimeType).getOrThrow()
                            }
                            isAttaching = false
                            uploaded.fold(
                                onSuccess = { attachmentPath ->
                                    commit(insertEmbed(value, attachmentPath), isTyping = false)
                                    // Upload now if there's a connection; otherwise it waits in the queue.
                                    repository.syncInBackground(roomId)
                                },
                                onFailure = { error ->
                                    attachError = if (error is AttachmentTooBigException) AttachError.TOO_BIG else AttachError.OTHER
                                },
                            )
                        }
                    }
                    NoteEditorEvent.DismissAttachError -> attachError = null
                    NoteEditorEvent.Undo -> undoStack.removeLastOrNull()?.let { previous ->
                        redoStack += value
                        value = previous
                        lastTypingAt = 0L
                    }
                    NoteEditorEvent.Redo -> redoStack.removeLastOrNull()?.let { next ->
                        undoStack += value
                        value = next
                        lastTypingAt = 0L
                    }
                    is NoteEditorEvent.SaveLink -> linkEdit?.let { replaceLink(it, event.link.toMarkdown()) }
                    NoteEditorEvent.RemoveLink -> linkEdit?.let { replaceLink(it, it.link.label()) }
                    NoteEditorEvent.DismissLinkEdit -> linkEdit = null
                    NoteEditorEvent.Save -> if (hasChanges()) save() else navigator.onDone()
                    NoteEditorEvent.Back -> if (hasChanges()) showSaveChangesDialog = true else navigator.onDone()
                    NoteEditorEvent.DiscardChanges -> {
                        showSaveChangesDialog = false
                        navigator.onDone()
                    }
                    NoteEditorEvent.DismissSaveChangesDialog -> showSaveChangesDialog = false
                }
            },
        )
    }

    /**
     * Writes the attachment at the cursor: a photo as `![[name]]` on a line of its own, so it shows in the note;
     * other files as a `[[name]]` link. Obsidian finds both by name.
     */
    private fun insertEmbed(current: TextFieldValue, attachmentPath: String): TextFieldValue {
        val text = current.text
        val at = maxOf(current.selection.start, current.selection.end)
        val name = attachmentPath.substringAfterLast('/')
        val embed = if (NoteEmbeds.isImage(attachmentPath)) {
            // A paragraph of its own (blank lines around it), which every Markdown app shows as a photo.
            val before = when {
                at == 0 || text.substring(0, at).endsWith("\n\n") -> ""
                text[at - 1] == '\n' -> "\n"
                else -> "\n\n"
            }
            val after = if (text.substring(at).startsWith("\n\n")) "" else "\n\n"
            "$before![[$name]]$after"
        } else {
            "[[$name]]"
        }
        return TextFieldValue(text.substring(0, at) + embed + text.substring(at), TextRange(at + embed.length))
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
        /** A pause in typing this long starts a new undo step. */
        const val TYPING_PAUSE_MILLIS = 1_000L

        /** Undo reaches back this many steps. */
        const val MAX_UNDO_STEPS = 100

        /** Between this phone's version and the other person's when combining: a plain line, clear in any language. */
        const val CONFLICT_SEPARATOR = "\n\n———\n\n"
    }
}
