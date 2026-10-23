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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import co.artise.android.notes.api.NoteAttachment
import co.artise.android.notes.api.NotesEntryPoint
import co.artise.android.notes.api.NotesRepository
import co.artise.android.notes.impl.sync.NotesLiveUpdates
import co.artise.android.notes.impl.ui.chart.NotesChartNode
import co.artise.android.notes.impl.ui.chats.NotesChatsNode
import co.artise.android.notes.impl.ui.choices.NotesChoicesNode
import co.artise.android.notes.impl.ui.dashboard.NotesDashboardNode
import co.artise.android.notes.impl.ui.document.NotesDocumentNode
import co.artise.android.notes.impl.ui.editor.NoteEditorNode
import co.artise.android.notes.impl.ui.folder.NotesFolderNode
import co.artise.android.notes.impl.ui.graph.NotesGraphNode
import co.artise.android.notes.impl.ui.history.NoteHistoryNode
import co.artise.android.notes.impl.ui.history.NoteVersionNode
import co.artise.android.notes.impl.ui.history.NoteVersionPresenter
import co.artise.android.notes.impl.ui.history.NotesChangesNode
import co.artise.android.notes.impl.ui.media.NotesMediaNode
import co.artise.android.notes.impl.ui.note.NoteNode
import co.artise.android.notes.impl.ui.search.NotesSearchNode
import co.artise.android.workspace.api.WorkspaceAccent
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
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

/** Chats with notes → a chat's folders → a note → the editor, with search, version choices and change history per chat. */
@ContributesNode(SessionScope::class)
@AssistedInject
class NotesFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val repository: NotesRepository,
    private val liveUpdates: NotesLiveUpdates,
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

        /** [opensDrawn] `false`: as a note, even when the phone's copy says it's a dashboard. */
        @Parcelize
        data class Note(val roomId: RoomId, val path: String, val opensDrawn: Boolean = true) : NavTarget

        @Parcelize
        data class Search(val roomId: RoomId) : NavTarget

        /** [resolveEditId] set: combine both versions of that conflicted edit. */
        @Parcelize
        data class Editor(val roomId: RoomId, val path: String, val resolveEditId: Long?) : NavTarget

        @Parcelize
        data class Choices(val roomId: RoomId) : NavTarget

        @Parcelize
        data class Map(val roomId: RoomId) : NavTarget

        /** A chat's photos and files, and the notes that use them. */
        @Parcelize
        data class Media(val roomId: RoomId) : NavTarget

        /** A PDF or photo of the notes, in the app's own viewer. */
        @Parcelize
        data class Document(val roomId: RoomId, val path: String) : NavTarget

        /** A chart as the server draws it. */
        @Parcelize
        data class Chart(val roomId: RoomId, val path: String) : NavTarget

        /** A dashboard's page (or a business's report page) as the server draws it. */
        @Parcelize
        data class Dashboard(val roomId: RoomId, val path: String) : NavTarget

        /** Every change to a chat's notes: who it was for and how it was made. */
        @Parcelize
        data class Changes(val roomId: RoomId) : NavTarget

        @Parcelize
        data class NoteHistory(val roomId: RoomId, val path: String) : NavTarget

        /** A note at one version, read-only; [savedAt] says when, for the title. */
        @Parcelize
        data class NoteVersion(val roomId: RoomId, val path: String, val commit: String, val savedAt: String) : NavTarget
    }

    override fun onBuilt() {
        super.onBuilt()
        lifecycleScope.launch {
            // Only while notes are on screen; coming back to the foreground starts again, which repeats the
            // ETag check for chats without the live state event.
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val entryRoom = listOfNotNull(plugins.filterIsInstance<NotesEntryPoint.Params>().firstOrNull()?.roomId)
                liveUpdates.keepUpToDate(entryRoom + repository.cachedChats().map { it.roomId })
            }
        }
    }

    // Shared by every screen: they all move within the flow the same way.
    private val navigation = object :
        NotesChatsNode.Callback,
        NotesFolderNode.Callback,
        NoteNode.Callback,
        NotesSearchNode.Callback,
        NotesChoicesNode.Callback,
        NotesGraphNode.Callback,
        NotesMediaNode.Callback,
        NotesChangesNode.Callback,
        NoteHistoryNode.Callback,
        NotesChartNode.Callback,
        NotesDashboardNode.Callback {
        override fun openChat(roomId: RoomId) = backstack.push(NavTarget.Folder(roomId, folder = ""))

        override fun openFolder(roomId: RoomId, folder: String) = backstack.push(NavTarget.Folder(roomId, folder))

        override fun openNote(roomId: RoomId, path: String) = backstack.push(NavTarget.Note(roomId, path))

        override fun openSearch(roomId: RoomId) = backstack.push(NavTarget.Search(roomId))

        override fun openEditor(roomId: RoomId, path: String) = backstack.push(NavTarget.Editor(roomId, path, resolveEditId = null))

        override fun openChoices(roomId: RoomId) = backstack.push(NavTarget.Choices(roomId))

        override fun openMap(roomId: RoomId) = backstack.push(NavTarget.Map(roomId))

        override fun openMedia(roomId: RoomId) = backstack.push(NavTarget.Media(roomId))

        override fun openDocument(roomId: RoomId, path: String) = backstack.push(NavTarget.Document(roomId, path))

        override fun openChart(roomId: RoomId, path: String) = backstack.push(NavTarget.Chart(roomId, path))

        override fun openDashboard(roomId: RoomId, path: String) = backstack.push(NavTarget.Dashboard(roomId, path))

        override fun editChart(roomId: RoomId, path: String) = openEditor(roomId, path)

        override fun showDrawn(roomId: RoomId, path: String, kind: NoteAttachment.Kind) =
            backstack.replace(NotesEntry.targetFor(roomId, path, kind))

        override fun showAsNote(roomId: RoomId, path: String) = backstack.replace(NavTarget.Note(roomId, path, opensDrawn = false))

        override fun openHistory(roomId: RoomId) = backstack.push(NavTarget.Changes(roomId))

        override fun openNoteHistory(roomId: RoomId, path: String) = backstack.push(NavTarget.NoteHistory(roomId, path))

        override fun openNoteVersion(roomId: RoomId, path: String, commit: String, savedAt: String) =
            backstack.push(NavTarget.NoteVersion(roomId, path, commit, savedAt))

        override fun combine(roomId: RoomId, path: String, editId: Long) = backstack.push(NavTarget.Editor(roomId, path, editId))

        override fun showRenamedNote(roomId: RoomId, newPath: String) = backstack.replace(NavTarget.Note(roomId, newPath))
    }

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node = when (navTarget) {
        NavTarget.Chats -> createNode<NotesChatsNode>(buildContext, listOf(navigation))
        is NavTarget.Folder -> createNode<NotesFolderNode>(buildContext, listOf(NotesFolderNode.Inputs(navTarget.roomId, navTarget.folder), navigation))
        is NavTarget.Note -> createNode<NoteNode>(buildContext, listOf(NoteNode.Inputs(navTarget.roomId, navTarget.path, navTarget.opensDrawn), navigation))
        is NavTarget.Search -> createNode<NotesSearchNode>(buildContext, listOf(NotesSearchNode.Inputs(navTarget.roomId), navigation))
        is NavTarget.Editor -> createNode<NoteEditorNode>(
            buildContext,
            listOf(NoteEditorNode.Inputs(navTarget.roomId, navTarget.path, navTarget.resolveEditId)),
        )
        is NavTarget.Choices -> createNode<NotesChoicesNode>(buildContext, listOf(NotesChoicesNode.Inputs(navTarget.roomId), navigation))
        is NavTarget.Map -> createNode<NotesGraphNode>(buildContext, listOf(NotesGraphNode.Inputs(navTarget.roomId), navigation))
        is NavTarget.Media -> createNode<NotesMediaNode>(buildContext, listOf(NotesMediaNode.Inputs(navTarget.roomId), navigation))
        is NavTarget.Document -> createNode<NotesDocumentNode>(buildContext, listOf(NotesDocumentNode.Inputs(navTarget.roomId, navTarget.path)))
        is NavTarget.Chart -> createNode<NotesChartNode>(buildContext, listOf(NotesChartNode.Inputs(navTarget.roomId, navTarget.path), navigation))
        is NavTarget.Dashboard -> createNode<NotesDashboardNode>(
            buildContext,
            listOf(NotesDashboardNode.Inputs(navTarget.roomId, navTarget.path), navigation),
        )
        is NavTarget.Changes -> createNode<NotesChangesNode>(buildContext, listOf(NotesChangesNode.Inputs(navTarget.roomId), navigation))
        is NavTarget.NoteHistory -> createNode<NoteHistoryNode>(buildContext, listOf(NoteHistoryNode.Inputs(navTarget.roomId, navTarget.path), navigation))
        is NavTarget.NoteVersion -> createNode<NoteVersionNode>(
            buildContext,
            listOf(NoteVersionNode.Inputs(NoteVersionPresenter.Version(navTarget.roomId, navTarget.path, navTarget.commit, navTarget.savedAt))),
        )
    }

    @Composable
    override fun View(modifier: Modifier) {
        // The workspace's accent on the notes screens' buttons and highlights.
        WorkspaceAccent {
            BackstackView(modifier)
        }
    }

    private companion object {
        fun initialTarget(plugins: List<Plugin>): NavTarget {
            val params = plugins.filterIsInstance<NotesEntryPoint.Params>().firstOrNull()
            return NotesEntry.initialTarget(params?.roomId, params?.openPath, params?.openKind)
        }
    }
}
