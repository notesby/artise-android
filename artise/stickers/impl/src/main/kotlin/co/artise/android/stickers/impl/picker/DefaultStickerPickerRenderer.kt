/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalConfiguration
import co.artise.android.stickers.api.StickerPickerRenderer
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.room.JoinedRoom

@ContributesBinding(SessionScope::class)
class DefaultStickerPickerRenderer(
    private val presenterFactory: StickerPickerPresenter.Factory,
) : StickerPickerRenderer {
    @Composable
    override fun Render(room: JoinedRoom, onDismiss: () -> Unit) {
        val language = LocalConfiguration.current.locales[0].language
        val currentOnDismiss by rememberUpdatedState(onDismiss)
        val presenter = remember(room, language) { presenterFactory.create(room, language) { currentOnDismiss() } }
        StickerPickerView(state = presenter.present(), onDismiss = onDismiss)
    }
}
