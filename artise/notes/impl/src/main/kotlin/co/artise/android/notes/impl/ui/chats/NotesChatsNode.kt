/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.chats

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.RoomId

@ContributesNode(SessionScope::class)
@AssistedInject
class NotesChatsNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: NotesChatsPresenter,
) : Node(buildContext, plugins = plugins) {
    interface Callback : Plugin {
        fun openChat(roomId: RoomId)
    }

    private val callback: Callback = callback()

    @Composable
    override fun View(modifier: Modifier) {
        NotesChatsView(
            state = presenter.present(),
            onBackClick = ::navigateUp,
            onChatClick = callback::openChat,
            modifier = modifier,
        )
    }
}
