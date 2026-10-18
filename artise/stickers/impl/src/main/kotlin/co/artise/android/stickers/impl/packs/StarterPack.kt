/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl.packs

import android.content.Context
import co.artise.android.stickers.impl.Sticker
import co.artise.android.stickers.impl.StickerSource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The stickers every Artise user has: Microsoft's Fluent Emoji 3D (MIT License), in the app's assets
 * (`assets/stickers/starter`, with `pack.json` and the license).
 */
interface StarterPack {
    fun stickers(language: String): List<Sticker>

    fun bytes(assetPath: String): ByteArray
}

@Serializable
internal data class StarterPackManifest(val stickers: List<Entry>) {
    @Serializable
    data class Entry(val id: String, val en: String, val es: String, val w: Int, val h: Int, val size: Long)
}

internal object StarterPackParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun stickers(manifest: String, language: String): List<Sticker> = json.decodeFromString<StarterPackManifest>(manifest).stickers.map {
        Sticker(
            id = "starter:${it.id}",
            description = if (language == "es") it.es else it.en,
            source = StickerSource.Starter("${DefaultStarterPack.FOLDER}/${it.id}.webp"),
            width = it.w,
            height = it.h,
            mimeType = "image/webp",
            size = it.size,
        )
    }
}

@ContributesBinding(AppScope::class)
class DefaultStarterPack(
    @ApplicationContext private val context: Context,
) : StarterPack {
    private val manifest by lazy { context.assets.open("$FOLDER/pack.json").use { it.readBytes().decodeToString() } }

    override fun stickers(language: String): List<Sticker> = StarterPackParser.stickers(manifest, language)

    override fun bytes(assetPath: String): ByteArray = context.assets.open(assetPath).use { it.readBytes() }

    companion object {
        const val FOLDER = "stickers/starter"
    }
}
