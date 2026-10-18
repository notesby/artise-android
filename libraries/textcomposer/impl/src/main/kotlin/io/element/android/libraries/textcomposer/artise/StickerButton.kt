/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.textcomposer.artise

import androidx.compose.runtime.staticCompositionLocalOf

/** Artise: a sticker button in the composer, shown when a screen provides it (the chat screen does). */
data class StickerButton(val contentDescription: String, val onClick: () -> Unit)

/**
 * No sticker button unless a screen provides one. A composition local rather than a parameter, so neither Element's
 * composer nor the screens between the chat and it need changes.
 */
@Suppress("CompositionLocalAllowlist")
val LocalStickerButton = staticCompositionLocalOf<StickerButton?> { null }
