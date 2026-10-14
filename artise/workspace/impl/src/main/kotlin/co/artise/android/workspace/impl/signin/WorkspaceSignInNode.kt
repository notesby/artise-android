/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.signin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import co.artise.android.workspace.api.WorkspaceSignInEntryPoint
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.inputs

@ContributesNode(AppScope::class)
@AssistedInject
class WorkspaceSignInNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: WorkspaceSignInPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    private val callback: WorkspaceSignInEntryPoint.Callback = callback()
    private val presenter = presenterFactory.create(inputs<WorkspaceSignInEntryPoint.Params>())

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        LaunchedEffect(state.readyServerName) {
            state.readyServerName?.let {
                state.eventSink(WorkspaceSignInEvent.ReadyHandled)
                callback.onWorkspaceReady(it)
            }
        }
        WorkspaceSignInView(state = state, onBackClick = ::navigateUp, modifier = modifier)
    }
}
