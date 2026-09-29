/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.common

/** Why a name typed for a new or renamed note can't be used. */
enum class NoteNameProblem {
    /** Empty, or has a "/" (which would make a folder) or starts with "." (hidden on the server). */
    INVALID,

    /** A note with that name is already in the folder. */
    EXISTS,
}

/** Turns what people type ("Súper", "Lista de la compra") into note paths, and checks them. */
object NoteNames {
    /** The path for [name] in [folder]: "Recetas" + "Mole" → "Recetas/Mole.md". */
    fun pathFor(folder: String, name: String): String {
        val file = name.trim().removeSuffix(".md") + ".md"
        return if (folder.isEmpty()) file else "$folder/$file"
    }

    /** The problem with [name] in [folder], or `null` if it can be used. [existingPaths] are the chat's files. */
    fun problemWith(folder: String, name: String, existingPaths: Collection<String>): NoteNameProblem? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() || trimmed.contains('/') || trimmed.startsWith('.') -> NoteNameProblem.INVALID
            existingPaths.any { it.equals(pathFor(folder, trimmed), ignoreCase = true) } -> NoteNameProblem.EXISTS
            else -> null
        }
    }
}
