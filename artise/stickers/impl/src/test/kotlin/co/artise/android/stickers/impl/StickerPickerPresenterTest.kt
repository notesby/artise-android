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
import co.artise.android.stickers.impl.packs.StickerRepository
import co.artise.android.stickers.impl.picker.MakingState
import co.artise.android.stickers.impl.picker.StickerPickerEvent
import co.artise.android.stickers.impl.picker.StickerPickerPresenter
import co.artise.android.stickers.impl.picker.StickerProblem
import co.artise.android.stickers.impl.picker.StickerTab
import co.artise.android.stickers.impl.send.StickerSender
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class StickerPickerPresenterTest {
    private val starter = Sticker("starter:coffee", "Café", StickerSource.Starter("stickers/starter/coffee.webp"), 256, 256, "image/webp", 3)

    private class FakeStickerRepository(private val starter: List<Sticker>) : StickerRepository {
        val mine = MutableStateFlow<List<Sticker>>(emptyList())
        var failSaving = false
        override val myStickers: StateFlow<List<Sticker>> = mine

        override suspend fun refresh() = Result.success(Unit)

        override fun starterStickers(language: String) = starter

        override suspend fun addToMine(picture: StickerPicture, description: String): Result<Sticker> {
            if (failSaving) return Result.failure(IllegalStateException("offline"))
            val sticker = Sticker("sticker_1", description, StickerSource.Uploaded("mxc://x/1"), picture.width, picture.height, picture.mimeType, 1)
            mine.value = mine.value + sticker
            return Result.success(sticker)
        }

        override suspend fun addUploaded(sticker: Sticker): Result<Sticker> {
            val saved = sticker.copy(id = "sticker_${mine.value.size + 1}")
            mine.value = mine.value + saved
            return Result.success(saved)
        }

        override suspend fun remove(sticker: Sticker): Result<Unit> {
            mine.value = mine.value - sticker
            return Result.success(Unit)
        }

        override suspend fun mxcUrlFor(sticker: Sticker) = Result.success("mxc://x/${sticker.id}")
    }

    private val sent = mutableListOf<String>()
    private var sendResult: Result<Unit> = Result.success(Unit)
    private val room = FakeJoinedRoom(sendRawResult = { type, _ ->
        sent += type
        sendResult
    })
    private val made = MadeSticker(StickerPicture(byteArrayOf(1), 300, 200, "image/webp"), isCutOut = true)

    private fun presenter(repository: FakeStickerRepository, onSent: () -> Unit = {}, maker: StickerMaker = StickerMaker { Result.success(made) }) =
        StickerPickerPresenter(room, "es", onSent, repository, StickerSender(repository), maker)

    /** With no stickers of their own yet, the starter pack shows first; tapping one sends it and closes the picker. */
    @Test
    fun `tapping a sticker sends it`() = runTest {
        var closed = false
        presenter(FakeStickerRepository(listOf(starter)), onSent = { closed = true }).test {
            val initial = awaitItem()
            assertThat(initial.tab).isEqualTo(StickerTab.STARTER)
            initial.eventSink(StickerPickerEvent.Send(starter))
            consumeItemsUntilPredicate { it.sendingId == starter.id }
            consumeItemsUntilPredicate { it.sendingId == null }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(sent).containsExactly("m.sticker")
        assertThat(closed).isTrue()
    }

    /** A sticker that can't be sent says so, and the picker stays open. */
    @Test
    fun `send failure is explained`() = runTest {
        sendResult = Result.failure(IllegalStateException("offline"))
        var closed = false
        presenter(FakeStickerRepository(listOf(starter)), onSent = { closed = true }).test {
            awaitItem().eventSink(StickerPickerEvent.Send(starter))
            assertThat(consumeItemsUntilPredicate { it.problem != null }.last().problem).isEqualTo(StickerProblem.SEND_FAILED)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(closed).isFalse()
    }

    /** A photo becomes a sticker, shown first; "Save and send" keeps it in "Mine" and sends it. */
    @Test
    fun `making a sticker from a photo`() = runTest {
        val repository = FakeStickerRepository(listOf(starter))
        presenter(repository).test {
            awaitItem().eventSink(StickerPickerEvent.Make("content://photo/1"))
            val ready = consumeItemsUntilPredicate { it.making is MakingState.Ready }.last()
            assertThat((ready.making as MakingState.Ready).made).isEqualTo(made)
            ready.eventSink(StickerPickerEvent.SaveMade(andSend = true))
            val saved = consumeItemsUntilPredicate { it.making == MakingState.Idle && it.mine.isNotEmpty() }.last()
            assertThat(saved.tab).isEqualTo(StickerTab.MINE)
            consumeItemsUntilPredicate { it.sendingId == null && sent.isNotEmpty() }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(sent).containsExactly("m.sticker")
    }

    /** If saving fails, the made sticker stays on screen to try again. */
    @Test
    fun `saving failure keeps the sticker`() = runTest {
        val repository = FakeStickerRepository(listOf(starter)).apply { failSaving = true }
        presenter(repository).test {
            awaitItem().eventSink(StickerPickerEvent.Make("content://photo/1"))
            consumeItemsUntilPredicate { it.making is MakingState.Ready }.last().eventSink(StickerPickerEvent.SaveMade(andSend = false))
            val failed = consumeItemsUntilPredicate { it.problem != null }.last()
            assertThat(failed.problem).isEqualTo(StickerProblem.SAVE_FAILED)
            assertThat(failed.making).isInstanceOf(MakingState.Ready::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A photo that can't be read says so. */
    @Test
    fun `making failure is explained`() = runTest {
        presenter(FakeStickerRepository(listOf(starter)), maker = { Result.failure(IllegalStateException("bad photo")) }).test {
            awaitItem().eventSink(StickerPickerEvent.Make("content://photo/1"))
            assertThat(consumeItemsUntilPredicate { it.problem != null }.last().problem).isEqualTo(StickerProblem.MAKE_FAILED)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Removing one of your stickers asks first. */
    @Test
    fun `removing asks first`() = runTest {
        val repository = FakeStickerRepository(listOf(starter))
        val mine = Sticker("sticker_1", "Sticker", StickerSource.Uploaded("mxc://x/1"), 1, 1, "image/webp", 1)
        repository.mine.value = listOf(mine)
        presenter(repository).test {
            val initial = consumeItemsUntilPredicate { it.mine.isNotEmpty() }.last()
            assertThat(initial.tab).isEqualTo(StickerTab.MINE)
            initial.eventSink(StickerPickerEvent.StartRemove(mine))
            consumeItemsUntilPredicate { it.removing == mine }.last().eventSink(StickerPickerEvent.ConfirmRemove)
            assertThat(consumeItemsUntilPredicate { it.mine.isEmpty() }.last().removing).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
