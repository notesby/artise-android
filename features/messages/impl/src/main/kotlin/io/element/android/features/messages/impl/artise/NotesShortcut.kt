/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.artise

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Artise: opens this chat's notes, from an icon next to the call button. A composition local, so the screens between
 * the chat and its top bar need no changes; threads don't provide it, so the icon shows only in the chat itself.
 */
@Suppress("CompositionLocalAllowlist")
val LocalOpenNotes = staticCompositionLocalOf<(() -> Unit)?> { null }
