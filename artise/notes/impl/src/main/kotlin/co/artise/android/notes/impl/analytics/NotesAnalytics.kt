/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.analytics

import com.bumble.appyx.core.lifecycle.subscribe
import com.bumble.appyx.core.node.Node
import im.vector.app.features.analytics.itf.VectorAnalyticsEvent
import im.vector.app.features.analytics.itf.VectorAnalyticsScreen
import io.element.android.services.analytics.api.AnalyticsService

/**
 * How notes are used, for people who said yes on "Help improve Artise". Only what was done and the kind of file:
 * never a note's name, a chat's name or any text.
 */
enum class NotesScreen : VectorAnalyticsScreen {
    NotesChats,
    NotesFolder,
    Note,
    NoteEditor,
    NotesSearch,
    NotesMap,
    NotesChoices,
    NotesMedia;

    override fun getName(): String = name

    override fun getProperties(): Map<String, Any>? = null
}

enum class NotesAction {
    NoteCreated,
    NoteSaved,
    NoteDeleted,
    NoteRenamed,
    AttachmentAdded,
    UploadRetried,
    UploadCancelled,
    ChoiceMade,
    SearchUsed,
    MediaDeleted,
}

/** One notes action; [kind] says "photo" or "document" for attachments, or which choice was made. */
data class NotesEvent(val action: NotesAction, val kind: String? = null) : VectorAnalyticsEvent {
    override fun getName(): String = "Notes${action.name}"

    override fun getProperties(): Map<String, Any>? = kind?.let { mapOf("kind" to it) }
}

/** Records [screen] each time this node's screen is shown. */
fun Node.trackScreen(analyticsService: AnalyticsService, screen: NotesScreen) {
    lifecycle.subscribe(onResume = { analyticsService.screen(screen) })
}
