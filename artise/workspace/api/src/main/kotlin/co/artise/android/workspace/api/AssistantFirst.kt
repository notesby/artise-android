/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.api

/**
 * The chat list with Ari's chat first: the direct chat whose other person is [assistantUserId]. The rest keeps its order; with no
 * assistant (or no such chat) nothing moves.
 */
fun <T> List<T>.withAssistantFirst(assistantUserId: String?, isAssistantChat: (T, String) -> Boolean): List<T> {
    if (assistantUserId == null) return this
    val index = indexOfFirst { isAssistantChat(it, assistantUserId) }
    if (index <= 0) return this
    return listOf(this[index]) + subList(0, index) + subList(index + 1, size)
}
