/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl

import co.artise.android.stickers.impl.packs.DefaultStickerRepository
import co.artise.android.stickers.impl.packs.ImagePack
import co.artise.android.stickers.impl.packs.RecentAccountData
import co.artise.android.stickers.impl.packs.StarterPack
import co.artise.android.stickers.impl.packs.StickerPicture
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID_2
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class StickerRepositoryTest {
    private val accountData = mutableMapOf<String, String>()
    private val uploads = mutableListOf<Int>()
    private val client = FakeMatrixClient(
        getAccountDataLambda = { type -> Result.success(accountData[type]) },
        setAccountDataLambda = { type, content ->
            accountData[type] = content
            Result.success(Unit)
        },
    ).apply { givenUploadMediaResult(Result.success("mxc://artise.co/new")) }
    private val starterPack = object : StarterPack {
        override fun stickers(language: String) = emptyList<Sticker>()

        override fun bytes(assetPath: String): ByteArray {
            uploads += 1
            return byteArrayOf(1, 2, 3)
        }
    }

    private val clock = FakeSystemClock()
    private val recent = RecentAccountData(clock)

    private fun TestScope.repository(matrixClient: FakeMatrixClient = client) =
        DefaultStickerRepository(matrixClient, starterPack, testCoroutineDispatchers(), recent)

    /** Like the SDK: reads come from a local store that only gets this device's writes at the next [sync]. */
    private class LaggingAccountData {
        val server = mutableMapOf<String, String>()
        private val store = mutableMapOf<String, String>()
        val client = FakeMatrixClient(
            getAccountDataLambda = { type -> Result.success(store[type]) },
            setAccountDataLambda = { type, content ->
                server[type] = content
                Result.success(Unit)
            },
        ).apply { givenUploadMediaResult(Result.success("mxc://artise.co/new")) }

        fun sync() = store.putAll(server)
    }

    private fun aPicture() = StickerPicture(byteArrayOf(9), 300, 200, "image/webp")

    /** A sticker made from a photo is uploaded and kept in the account's pack, so every device has it. */
    @Test
    fun `made stickers are kept in the account`() = runTest {
        val repository = repository()
        val saved = repository.addToMine(StickerPicture(byteArrayOf(9), 300, 200, "image/webp"), "Sticker").getOrThrow()
        assertThat(saved.id).isEqualTo("sticker_1")
        assertThat(saved.source).isEqualTo(StickerSource.Uploaded("mxc://artise.co/new"))
        assertThat(repository.myStickers.value).containsExactly(saved)
        assertThat(ImagePack.stickers(accountData[ImagePack.USER_PACK_EVENT_TYPE])).containsExactly(saved)

        repository.remove(saved).getOrThrow()
        assertThat(repository.myStickers.value).isEmpty()
    }

    /** A starter sticker is uploaded the first time it's sent, then the same picture is reused. */
    @Test
    fun `starter stickers upload once`() = runTest {
        val repository = repository()
        val coffee = Sticker("starter:coffee", "Café", StickerSource.Starter("stickers/starter/coffee.webp"), 256, 256, "image/webp", 3)
        assertThat(repository.mxcUrlFor(coffee).getOrThrow()).isEqualTo("mxc://artise.co/new")
        assertThat(repository.mxcUrlFor(coffee).getOrThrow()).isEqualTo("mxc://artise.co/new")
        assertThat(uploads).hasSize(1)
    }

    /** Stickers saved on another device show after a refresh. */
    @Test
    fun `refresh reads the account`() = runTest {
        accountData[ImagePack.USER_PACK_EVENT_TYPE] = """{"images": {"sticker_7": {"url": "mxc://x/y", "body": "Perro"}}}"""
        val repository = repository()
        repository.refresh().getOrThrow()
        assertThat(repository.myStickers.value.map { it.description }).containsExactly("Perro")
    }

    /** Two stickers saved before the next sync both stay: the second save adds to the pack the first one wrote, not the old one. */
    @Test
    fun `stickers saved in a row both stay before a sync`() = runTest {
        val account = LaggingAccountData()
        val repository = repository(account.client)
        val first = repository.addToMine(aPicture(), "Uno").getOrThrow()
        val second = repository.addToMine(aPicture(), "Dos").getOrThrow()
        assertThat(second.id).isNotEqualTo(first.id)
        assertThat(ImagePack.stickers(account.server[ImagePack.USER_PACK_EVENT_TYPE]).map { it.description }).containsExactly("Uno", "Dos")

        // Removing one right away keeps the other.
        repository.remove(first).getOrThrow()
        assertThat(ImagePack.stickers(account.server[ImagePack.USER_PACK_EVENT_TYPE]).map { it.description }).containsExactly("Dos")
        account.sync()
        repository.refresh().getOrThrow()
        assertThat(repository.myStickers.value.map { it.description }).containsExactly("Dos")
    }

    /** A refresh before the sync doesn't bring back the old pack. */
    @Test
    fun `a refresh before the sync keeps what was just saved`() = runTest {
        val account = LaggingAccountData()
        val repository = repository(account.client)
        repository.addToMine(aPicture(), "Uno").getOrThrow()
        repository.refresh().getOrThrow()
        assertThat(repository.myStickers.value.map { it.description }).containsExactly("Uno")
    }

    /** A starter sticker sent twice before the next sync is uploaded once. */
    @Test
    fun `starter stickers upload once before a sync`() = runTest {
        val account = LaggingAccountData()
        val repository = repository(account.client)
        val coffee = Sticker("starter:coffee", "Café", StickerSource.Starter("stickers/starter/coffee.webp"), 256, 256, "image/webp", 3)
        repository.mxcUrlFor(coffee).getOrThrow()
        repository.mxcUrlFor(coffee).getOrThrow()
        assertThat(uploads).hasSize(1)
    }

    /** Once the store has caught up, or after 30 seconds, the store wins again: another device may have changed the pack since. */
    @Test
    fun `the store wins once it caught up or after 30 seconds`() {
        recent.remember(A_SESSION_ID, "pack", "mine")
        clock.epochMillisResult += 29_000
        assertThat(recent.pick(A_SESSION_ID, "pack", "old")).isEqualTo("mine")
        clock.epochMillisResult += 2_000
        assertThat(recent.pick(A_SESSION_ID, "pack", "old")).isEqualTo("old")

        recent.remember(A_SESSION_ID, "pack", "mine")
        assertThat(recent.pick(A_SESSION_ID, "pack", "mine")).isEqualTo("mine")
        assertThat(recent.pick(A_SESSION_ID, "pack", "from another device")).isEqualTo("from another device")
    }

    /** Stickers shared from other apps get a repository each; two in a row still both stay, and other accounts aren't mixed in. */
    @Test
    fun `separate repositories of one account share what was just written`() = runTest {
        val account = LaggingAccountData()
        repository(account.client).addToMine(aPicture(), "Uno").getOrThrow()
        repository(account.client).addToMine(aPicture(), "Dos").getOrThrow()
        assertThat(ImagePack.stickers(account.server[ImagePack.USER_PACK_EVENT_TYPE]).map { it.description }).containsExactly("Uno", "Dos")
        assertThat(recent.pick(A_SESSION_ID_2, ImagePack.USER_PACK_EVENT_TYPE, null)).isNull()
    }
}
