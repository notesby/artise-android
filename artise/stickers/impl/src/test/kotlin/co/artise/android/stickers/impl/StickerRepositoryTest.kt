/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl

import co.artise.android.stickers.impl.packs.DefaultStickerRepository
import co.artise.android.stickers.impl.packs.ImagePack
import co.artise.android.stickers.impl.packs.StarterPack
import co.artise.android.stickers.impl.packs.StickerPicture
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.testCoroutineDispatchers
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

    private fun kotlinx.coroutines.test.TestScope.repository() = DefaultStickerRepository(client, starterPack, testCoroutineDispatchers())

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
}
