/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.quiet.impl

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import co.artise.android.quiet.api.HoursWindow
import co.artise.android.quiet.api.NotificationHoursSettings
import co.artise.android.quiet.api.NotificationSchedule
import co.artise.android.quiet.api.QuietHours
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.collections.immutable.toImmutableMap
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
    private val store by lazy { Store(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)) }
    private val all by lazy { MutableStateFlow(store.load()) }

    override fun settings(sessionId: SessionId): Flow<NotificationHoursSettings> = all.map { it[sessionId.value] ?: NotificationHoursSettings() }

    override suspend fun set(sessionId: SessionId, settings: NotificationHoursSettings) = withContext(dispatchers.io) {
        val next = all.value + (sessionId.value to settings)
        store.save(next)
        all.value = next
    }

    override fun isQuietNow(sessionId: SessionId, isMention: Boolean): Boolean {
        val settings = all.value[sessionId.value] ?: return false
        return isQuiet(settings, ZonedDateTime.ofInstant(Instant.ofEpochMilli(clock.epochMillis()), ZoneId.systemDefault()), isMention)
    }

    /** Each workspace's notification hours, by session id. Quiet hours saved by an earlier version are read once and carried over. */
    internal class Store(private val preferences: SharedPreferences) {
        private val json = Json { ignoreUnknownKeys = true }

        fun load(): Map<String, NotificationHoursSettings> = runCatchingExceptions {
            preferences.getString(KEY, null)?.let { saved -> json.decodeFromString<Map<String, HoursDto>>(saved).mapValues { it.value.toSettings() } }
                ?: preferences.getString(
                    OLD_KEY,
                    null
                )?.let { saved -> json.decodeFromString<Map<String, QuietDto>>(saved).mapValues { it.value.toSettings() } }
                ?: emptyMap()
        }.getOrDefault(emptyMap())

        fun save(all: Map<String, NotificationHoursSettings>) = preferences.edit {
            putString(KEY, json.encodeToString(all.mapValues { HoursDto.of(it.value) }))
            remove(OLD_KEY)
        }
    }

    @Serializable
    internal data class WindowDto(val start: Int, val end: Int)

    @Serializable
    internal data class HoursDto(
        val enabled: Boolean,
        val schedule: NotificationSchedule,
        val window: WindowDto,
        val days: Map<DayOfWeek, WindowDto>,
        val mentions: Boolean,
    ) {
        fun toSettings() = NotificationHoursSettings(
            enabled = enabled,
            schedule = schedule,
            window = HoursWindow(window.start, window.end),
            days = days.mapValues { HoursWindow(it.value.start, it.value.end) }.toImmutableMap(),
            allowMentions = mentions,
        )

        companion object {
            fun of(s: NotificationHoursSettings) = HoursDto(
                enabled = s.enabled,
                schedule = s.schedule,
                window = WindowDto(s.window.startMinute, s.window.endMinute),
                days = s.days.mapValues { WindowDto(it.value.startMinute, it.value.endMinute) },
                mentions = s.allowMentions,
            )
        }
    }

    /** The quiet hours an earlier version saved: [start] to [end] silent, and the whole weekend when chosen. */
    @Serializable
    internal data class QuietDto(val enabled: Boolean, val start: Int, val end: Int, val weekend: Boolean, val mentions: Boolean) {
        fun toSettings() = NotificationHoursSettings.fromQuietHours(enabled, start, end, weekend, mentions)
    }

    companion object {
        private const val PREFERENCES = "artise_quiet_hours"
        private const val OLD_KEY = "settings"
        private const val KEY = "notification_hours"

        /** Whether a notification at [at] arrives without sound: outside the notification hours, unless it's a mention the person lets through. */
        fun isQuiet(settings: NotificationHoursSettings, at: ZonedDateTime, isMention: Boolean): Boolean =
            !settings.soundsAt(at.dayOfWeek, at.hour * 60 + at.minute, isMention)
    }
}
