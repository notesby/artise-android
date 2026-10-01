/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl

import co.artise.android.stickers.impl.maker.MadeSticker
import co.artise.android.stickers.impl.maker.StickerMaker
import co.artise.android.stickers.impl.packs.StickerPicture
import co.artise.android.stickers.impl.share.AddStickerEvent
import co.artise.android.stickers.impl.share.AddStickerPresenter
import co.artise.android.stickers.impl.share.AddStickerStep
import co.artise.android.stickers.impl.share.StickerAccount
import com.google.common.truth.Truth.assertThat
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AddStickerPresenterTest {
    private val made = MadeSticker(StickerPicture(byteArrayOf(1), 200, 200, "image/webp"), isCutOut = true)

    private class FakeStickerAccount(private val signedIn: Boolean, var saveResult: Result<Unit> = Result.success(Unit)) : StickerAccount {
        val saved = mutableListOf<MadeSticker>()

        override suspend fun isSignedIn() = signedIn

        override suspend fun addToMyStickers(made: MadeSticker): Result<Unit> = saveResult.onSuccess { saved += made }
    }

    /** A picture shared from another app is shown, then saved to the account's stickers. */
    @Test
    fun `shared picture is saved`() = runTest {
        val account = FakeStickerAccount(signedIn = true)
        AddStickerPresenter("content://share/1", account, StickerMaker { Result.success(made) }).test {
            val ready = consumeItemsUntilPredicate { it.step is AddStickerStep.Ready }.last()
            ready.eventSink(AddStickerEvent.Save)
            consumeItemsUntilPredicate { it.step == AddStickerStep.Saved }
        }
        assertThat(account.saved).containsExactly(made)
    }

    /** With nobody signed in, the screen says to sign in first. */
    @Test
    fun `signed out`() = runTest {
        AddStickerPresenter("content://share/1", FakeStickerAccount(signedIn = false), StickerMaker { Result.success(made) }).test {
            assertThat(consumeItemsUntilPredicate { it.step != AddStickerStep.Preparing }.last().step).isEqualTo(AddStickerStep.SignedOut)
        }
    }

    /** A picture that can't be read says so. */
    @Test
    fun `unreadable picture`() = runTest {
        AddStickerPresenter("content://share/1", FakeStickerAccount(signedIn = true), StickerMaker { Result.failure(IllegalStateException()) }).test {
            assertThat(consumeItemsUntilPredicate { it.step != AddStickerStep.Preparing }.last().step).isEqualTo(AddStickerStep.Failed)
        }
    }

    /** If saving fails (no connection), the sticker stays on screen with a note, to try again. */
    @Test
    fun `save failure can be retried`() = runTest {
        val account = FakeStickerAccount(signedIn = true, saveResult = Result.failure(IllegalStateException("offline")))
        AddStickerPresenter("content://share/1", account, StickerMaker { Result.success(made) }).test {
            consumeItemsUntilPredicate { it.step is AddStickerStep.Ready }.last().eventSink(AddStickerEvent.Save)
            val failed = consumeItemsUntilPredicate { it.saveFailed }.last()
            assertThat(failed.step).isEqualTo(AddStickerStep.Ready(made, isSaving = false))
            account.saveResult = Result.success(Unit)
            failed.eventSink(AddStickerEvent.Save)
            consumeItemsUntilPredicate { it.step == AddStickerStep.Saved }
        }
        assertThat(account.saved).containsExactly(made)
    }
}
