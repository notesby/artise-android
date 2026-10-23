/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.charteditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import co.artise.android.notes.impl.analytics.NotesScreen
import co.artise.android.notes.impl.analytics.trackScreen
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.inputs
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.services.analytics.api.AnalyticsService
import java.util.Locale

@ContributesNode(SessionScope::class)
@AssistedInject
class ChartEditorNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: ChartEditorPresenter.Factory,
    analyticsService: AnalyticsService,
) : Node(buildContext, plugins = plugins) {
    init {
        trackScreen(analyticsService, NotesScreen.ChartEditor)
    }

    interface Callback : Plugin {
        /** Saved: a new chart opens drawn; an edited one goes back to where it was opened from. */
        fun onChartSaved(roomId: RoomId, path: String, isNew: Boolean)
    }

    /** [newTitle] set: a new chart at [path], written on the first Save. */
    data class Inputs(val roomId: RoomId, val path: String, val newTitle: String?) : NodeInputs

    private val inputs: Inputs = inputs()
    private val callback: Callback = callback()
    private val presenter = presenterFactory.create(inputs.roomId, inputs.path, inputs.newTitle, Locale.getDefault())

    @Composable
    override fun View(modifier: Modifier) {
        ChartEditorView(
            state = presenter.present(),
            onBackClick = ::navigateUp,
            onDone = { callback.onChartSaved(inputs.roomId, inputs.path, isNew = inputs.newTitle != null) },
            modifier = modifier,
        )
    }
}
