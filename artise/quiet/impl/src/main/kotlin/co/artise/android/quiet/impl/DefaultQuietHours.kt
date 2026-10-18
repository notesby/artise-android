/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.quiet.impl

import android.content.Context
import androidx.core.content.edit
import co.artise.android.quiet.api.QuietHours
import co.artise.android.quiet.api.QuietHoursSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultQuietHours(
    @ApplicationContext private val context: Context,
    private val clock: SystemClock,
    private val dispatchers: CoroutineDispatchers,
) : QuietHours {
    private val preferences by lazy { context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }
    private val json = Json { ignoreUnknownKeys = true }
    private val all by lazy { MutableStateFlow(load()) }

    override fun settings(sessionId: SessionId): Flow<QuietHoursSettings> = all.map { it[sessionId.value]?.toSettings() ?: QuietHoursSettings() }

    override suspend fun set(sessionId: SessionId, settings: QuietHoursSettings) = withContext(dispatchers.io) {
        val next = all.value + (sessionId.value to Dto.of(settings))
        preferences.edit { putString(KEY, json.encodeToString(next)) }
        all.value = next
    }

    override fun isQuietNow(sessionId: SessionId, isMention: Boolean): Boolean {
        val settings = all.value[sessionId.value]?.toSettings() ?: return false
        return isQuiet(settings, ZonedDateTime.ofInstant(Instant.ofEpochMilli(clock.epochMillis()), ZoneId.systemDefault()), isMention)
    }

    private fun load(): Map<String, Dto> = runCatchingExceptions {
        preferences.getString(KEY, null)?.let { json.decodeFromString<Map<String, Dto>>(it) }.orEmpty()
    }.getOrDefault(emptyMap())

    @Serializable
    internal data class Dto(val enabled: Boolean, val start: Int, val end: Int, val weekend: Boolean, val mentions: Boolean) {
        fun toSettings() = QuietHoursSettings(enabled, start, end, weekend, mentions)

        companion object {
            fun of(s: QuietHoursSettings) = Dto(s.enabled, s.startMinute, s.endMinute, s.wholeWeekend, s.allowMentions)
        }
    }

    companion object {
        private const val PREFERENCES = "artise_quiet_hours"
        private const val KEY = "settings"

        /** Whether [at] falls in the quiet window (overnight when it wraps midnight), or anywhere on a weekend when chosen. */
        fun isQuiet(settings: QuietHoursSettings, at: ZonedDateTime, isMention: Boolean): Boolean {
            if (!settings.enabled) return false
            if (isMention && settings.allowMentions) return false
            if (settings.wholeWeekend && (at.dayOfWeek == DayOfWeek.SATURDAY || at.dayOfWeek == DayOfWeek.SUNDAY)) return true
            val minute = at.hour * 60 + at.minute
            return if (settings.startMinute <= settings.endMinute) {
                minute >= settings.startMinute && minute < settings.endMinute
            } else {
                minute >= settings.startMinute || minute < settings.endMinute
            }
        }
    }
}
