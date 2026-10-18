/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.history

import co.artise.android.notes.api.FileChange
import io.element.android.libraries.dateformatter.api.DateFormatter
import io.element.android.libraries.dateformatter.api.DateFormatterMode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** One day of history, newest first: "Today", "Yesterday", "Sunday 6 April"... */
data class HistoryDay(
    val label: String,
    val entries: ImmutableList<HistoryEntry>,
)

/** One change, or one version of a note: when, who it was for and how, the server's message, and the files it touched. */
data class HistoryEntry(
    val commit: String,
    val time: String,
    val label: ChangeLabel,
    val message: String,
    /** Empty in a note's own history. */
    val files: ImmutableList<HistoryFile>,
)

/** A file a change touched, and what tapping it does. */
data class HistoryFile(
    val path: String,
    val name: String,
    val change: FileChange,
    /** The old name of a renamed or copied file. */
    val fromName: String?,
    val target: HistoryFileTarget,
)

enum class HistoryFileTarget {
    /** A note that's still there: open it. */
    NOTE,

    /** A note that isn't there anymore: open its history, where its last version can be read. */
    NOTE_HISTORY,

    /** A photo or document that's still there: open it in another app. */
    FILE,

    /** A photo or document that isn't there anymore (it went to the server's trash): nothing to open. */
    NONE,
}

internal object NotesHistoryDates {
    /** [at] (ISO 8601 with an offset) in milliseconds, or `null` if the server sent something unreadable. */
    fun millisOf(at: String): Long? = try {
        OffsetDateTime.parse(at).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }

    /**
     * Groups [items] (newest first) into days in the phone's time zone, as the date formatter names them. Consecutive items
     * with the same day name share a day.
     */
    fun <T> groupByDay(items: List<T>, dateFormatter: DateFormatter, at: (T) -> String, entry: (T, time: String) -> HistoryEntry): ImmutableList<HistoryDay> {
        val days = mutableListOf<Pair<String, MutableList<HistoryEntry>>>()
        items.forEach { item ->
            val millis = millisOf(at(item))
            val day = millis?.let { dateFormatter.format(it, DateFormatterMode.Day, useRelative = true) }.orEmpty()
            val time = millis?.let { dateFormatter.format(it, DateFormatterMode.TimeOnly) }.orEmpty()
            if (days.lastOrNull()?.first != day) days += day to mutableListOf()
            days.last().second += entry(item, time)
        }
        return days.map { (label, entries) -> HistoryDay(label, entries.toImmutableList()) }.toImmutableList()
    }

    /** The extensions the server keeps as notes, with git history; anything else is a photo or document. */
    private val NOTE_EXTENSIONS = setOf("md", "txt", "csv", "json", "yaml", "yml")

    fun isNotePath(path: String): Boolean = path.substringAfterLast('/').substringAfterLast('.', "").lowercase() in NOTE_EXTENSIONS
}
