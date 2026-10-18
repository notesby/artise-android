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

    /**
     * [path] renamed to [newName] in the same folder. A note named "Mole" stays "Mole.md"; a file keeps its extension
     * when the new name leaves it out ("factura.pdf" renamed "recibo" → "recibo.pdf").
     */
    fun renamedPath(path: String, newName: String): String {
        val folder = path.substringBeforeLast('/', missingDelimiterValue = "")
        val name = newName.trim()
        val extension = path.substringAfterLast('/').substringAfterLast('.', missingDelimiterValue = "")
        val file = when {
            extension.isEmpty() -> name
            name.endsWith(".$extension", ignoreCase = true) -> name
            else -> "$name.$extension"
        }
        return if (folder.isEmpty()) file else "$folder/$file"
    }

    /** The problem with renaming [path] to [newName], or `null`; [existingPaths] are the chat's files. */
    fun renameProblem(path: String, newName: String, existingPaths: Collection<String>): NoteNameProblem? {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed.contains('/') || trimmed.startsWith('.')) return NoteNameProblem.INVALID
        val target = renamedPath(path, trimmed)
        return if (!target.equals(path, ignoreCase = true) && existingPaths.any { it.equals(target, ignoreCase = true) }) NoteNameProblem.EXISTS else null
    }

    /** [path] moved into [folder] ("" for the top level), keeping its name. */
    fun movedPath(path: String, folder: String): String {
        val name = path.substringAfterLast('/')
        return if (folder.isEmpty()) name else "$folder/$name"
    }

    /** The problem with a folder called [name] ("Postres", or "Recetas/Postres" for one inside another), or `null`. */
    fun folderProblem(name: String): NoteNameProblem? {
        val parts = name.trim().trim('/').split('/')
        return if (parts.any { it.isBlank() || it.startsWith('.') }) NoteNameProblem.INVALID else null
    }

    /**
     * The problem with renaming the folder [path] to [newName] in the same parent, or `null`; [existingPaths] are the
     * chat's files and folders (a file and a folder can't share a name).
     */
    fun folderRenameProblem(path: String, newName: String, existingPaths: Collection<String>): NoteNameProblem? {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed.contains('/') || trimmed.startsWith('.')) return NoteNameProblem.INVALID
        val target = movedPath(trimmed, path.substringBeforeLast('/', missingDelimiterValue = ""))
        return if (!target.equals(path, ignoreCase = true) && existingPaths.any { it.equals(target, ignoreCase = true) }) NoteNameProblem.EXISTS else null
    }

    /** "  Recetas / Postres/ " → "Recetas/Postres". */
    fun folderPath(name: String): String = name.trim().trim('/').split('/').joinToString("/") { it.trim() }
}
