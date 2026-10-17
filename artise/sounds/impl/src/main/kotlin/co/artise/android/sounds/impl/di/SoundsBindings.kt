/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.sounds.impl.di

import co.artise.android.sounds.api.ChatSoundState
import co.artise.android.sounds.impl.ChatSoundPresenter
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.di.RoomScope

@ContributesTo(RoomScope::class)
@BindingContainer
interface SoundsBindings {
    @Binds
    fun bindChatSoundPresenter(presenter: ChatSoundPresenter): Presenter<ChatSoundState>
}
