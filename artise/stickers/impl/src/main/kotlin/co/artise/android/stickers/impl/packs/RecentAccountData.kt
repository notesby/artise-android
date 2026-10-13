/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl.packs

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.services.toolbox.api.systemclock.SystemClock
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * What this device last wrote to each type of account data, and when.
 *
 * The SDK reads account data from its local store, which only changes at the next sync: right after a write it still returns the old
 * content, so a second sticker saved within a second would be added to the old pack and the first one lost. For 30 seconds after a
 * write, [pick] prefers what was written; once the store has it, or the window is over (another device may have changed it since),
 * the store wins again.
 *
 * One for the whole app, per account: stickers shared from other apps are saved by a repository of their own.
 */
@SingleIn(AppScope::class)
@Inject
class RecentAccountData(
    private val clock: SystemClock,
) {
    private data class Write(val content: String, val atMillis: Long)

    private val writes = ConcurrentHashMap<Pair<SessionId, String>, Write>()

    fun remember(sessionId: SessionId, eventType: String, content: String) {
        writes[sessionId to eventType] = Write(content, clock.epochMillis())
    }

    /** The content to use for [eventType], given what the store returned. */
    fun pick(sessionId: SessionId, eventType: String, stored: String?): String? {
        val key = sessionId to eventType
        val write = writes[key] ?: return stored
        val elapsed = clock.epochMillis() - write.atMillis
        return if (stored == write.content || elapsed !in 0..WINDOW.inWholeMilliseconds) {
            writes.remove(key, write)
            stored
        } else {
            write.content
        }
    }

    private companion object {
        val WINDOW: Duration = 30.seconds
    }
}
