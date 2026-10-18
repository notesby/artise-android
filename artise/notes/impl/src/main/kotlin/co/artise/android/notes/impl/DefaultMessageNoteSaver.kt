/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl

import co.artise.android.notes.api.MessageNoteSaver
import co.artise.android.notes.api.NotesRepository
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.services.toolbox.api.strings.StringProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import co.artise.android.notes.api.R as NotesApiR

@ContributesBinding(SessionScope::class)
class DefaultMessageNoteSaver(
    private val repository: NotesRepository,
    private val stringProvider: StringProvider,
) : MessageNoteSaver {
    override suspend fun save(roomId: RoomId, senderName: String, sentAtMillis: Long, text: String): Result<String> {
        val date = Date(sentAtMillis)
        val stamp = SimpleDateFormat("yyyy-MM-dd HHmm", Locale.ROOT).format(date)
        val shown = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date)
        val folder = stringProvider.getString(NotesApiR.string.artise_saved_messages_folder)
        val path = folder + "/" + stamp + " " + fileSafe(senderName) + ".md"
        val content = text.lines().joinToString("\n") { "> " + it } + "\n\n— " + senderName + ", " + shown + "\n"
        return repository.createNote(roomId, path, content).map { path }
    }

    // Folder and file names keep letters, numbers, spaces and dashes.
    private fun fileSafe(name: String): String = name.filter { it.isLetterOrDigit() || it == ' ' || it == '-' }
        .replace(Regex(" +"), " ")
        .trim()
        .take(40)
        .ifEmpty { "mensaje" }
}
