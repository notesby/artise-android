/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl.share

import co.artise.android.stickers.impl.maker.MadeSticker
import co.artise.android.stickers.impl.packs.DefaultStickerRepository
import co.artise.android.stickers.impl.packs.StarterPack
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.mapCatchingExceptions
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.sessionstorage.api.SessionStore

/** The account stickers shared from other apps go to: the one signed in on this phone (the latest, with several). */
interface StickerAccount {
    suspend fun isSignedIn(): Boolean

    suspend fun addToMyStickers(made: MadeSticker): Result<Unit>
}

@ContributesBinding(AppScope::class)
class DefaultStickerAccount(
    private val sessionStore: SessionStore,
    private val matrixClientProvider: MatrixClientProvider,
    private val starterPack: StarterPack,
    private val dispatchers: CoroutineDispatchers,
) : StickerAccount {
    override suspend fun isSignedIn(): Boolean = sessionStore.getLatestSession() != null

    override suspend fun addToMyStickers(made: MadeSticker): Result<Unit> {
        val session = sessionStore.getLatestSession() ?: return Result.failure(IllegalStateException("Not signed in"))
        return matrixClientProvider.getOrRestore(SessionId(session.userId)).mapCatchingExceptions { client ->
            DefaultStickerRepository(client, starterPack, dispatchers).addToMine(made.picture, STICKER_DESCRIPTION).getOrThrow()
        }.map { }
    }

    private companion object {
        const val STICKER_DESCRIPTION = "Sticker"
    }
}
