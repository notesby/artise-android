/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import co.artise.android.notes.api.NotesEntryPoint
import co.artise.android.notes.impl.ui.chats.NotesChatsNode
import co.artise.android.notes.impl.ui.choices.NotesChoicesNode
import co.artise.android.notes.impl.ui.editor.NoteEditorNode
import co.artise.android.notes.impl.ui.folder.NotesFolderNode
import co.artise.android.notes.impl.ui.note.NoteNode
import co.artise.android.notes.impl.ui.search.NotesSearchNode
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.navmodel.backstack.BackStack
import com.bumble.appyx.navmodel.backstack.operation.push
import com.bumble.appyx.navmodel.backstack.operation.replace
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.parcelize.Parcelize

/** Chats with notes → a chat's folders → a note → the editor, with search and version choices per chat. */
@ContributesNode(SessionScope::class)
@AssistedInject
class NotesFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
) : BaseFlowNode<NotesFlowNode.NavTarget>(
    backstack = BackStack(
        initialElement = initialTarget(plugins),
        savedStateMap = buildContext.savedStateMap,
    ),
    buildContext = buildContext,
    plugins = plugins,
) {
    sealed interface NavTarget : Parcelable {
        @Parcelize
        data object Chats : NavTarget

        @Parcelize
        data class Folder(val roomId: RoomId, val folder: String) : NavTarget

        @Parcelize
        data class Note(val roomId: RoomId, val path: String) : NavTarget

        @Parcelize
        data class Search(val roomId: RoomId) : NavTarget

        /** [resolveEditId] set: combine both versions of that conflicted edit. */
        @Parcelize
        data class Editor(val roomId: RoomId, val path: String, val resolveEditId: Long?) : NavTarget

        @Parcelize
        data class Choices(val roomId: RoomId) : NavTarget
    }

    // Shared by every screen: they all move within the flow the same way.
    private val navigation = object :
        NotesChatsNode.Callback,
        NotesFolderNode.Callback,
        NoteNode.Callback,
        NotesSearchNode.Callback,
        NotesChoicesNode.Callback {
        override fun openChat(roomId: RoomId) = backstack.push(NavTarget.Folder(roomId, folder = ""))

        override fun openFolder(roomId: RoomId, folder: String) = backstack.push(NavTarget.Folder(roomId, folder))

        override fun openNote(roomId: RoomId, path: String) = backstack.push(NavTarget.Note(roomId, path))

        override fun openSearch(roomId: RoomId) = backstack.push(NavTarget.Search(roomId))

        override fun openEditor(roomId: RoomId, path: String) = backstack.push(NavTarget.Editor(roomId, path, resolveEditId = null))

        override fun openChoices(roomId: RoomId) = backstack.push(NavTarget.Choices(roomId))

        override fun combine(roomId: RoomId, path: String, editId: Long) = backstack.push(NavTarget.Editor(roomId, path, editId))

        override fun showRenamedNote(roomId: RoomId, newPath: String) = backstack.replace(NavTarget.Note(roomId, newPath))
    }

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node = when (navTarget) {
        NavTarget.Chats -> createNode<NotesChatsNode>(buildContext, listOf(navigation))
        is NavTarget.Folder -> createNode<NotesFolderNode>(buildContext, listOf(NotesFolderNode.Inputs(navTarget.roomId, navTarget.folder), navigation))
        is NavTarget.Note -> createNode<NoteNode>(buildContext, listOf(NoteNode.Inputs(navTarget.roomId, navTarget.path), navigation))
        is NavTarget.Search -> createNode<NotesSearchNode>(buildContext, listOf(NotesSearchNode.Inputs(navTarget.roomId), navigation))
        is NavTarget.Editor -> createNode<NoteEditorNode>(
            buildContext,
            listOf(NoteEditorNode.Inputs(navTarget.roomId, navTarget.path, navTarget.resolveEditId)),
        )
        is NavTarget.Choices -> createNode<NotesChoicesNode>(buildContext, listOf(NotesChoicesNode.Inputs(navTarget.roomId), navigation))
    }

    @Composable
    override fun View(modifier: Modifier) {
        BackstackView(modifier)
    }

    private companion object {
        fun initialTarget(plugins: List<Plugin>): NavTarget {
            val roomId = plugins.filterIsInstance<NotesEntryPoint.Params>().firstOrNull()?.roomId
            return if (roomId == null) NavTarget.Chats else NavTarget.Folder(roomId, folder = "")
        }
    }
}
