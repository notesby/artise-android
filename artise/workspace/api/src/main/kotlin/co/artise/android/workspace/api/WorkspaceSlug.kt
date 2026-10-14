/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.api

/** The short name of a workspace, as people type it ("Acme " → "acme"). */
object WorkspaceSlug {
    private val VALID = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
    private const val MIN_LENGTH = 3
    private const val MAX_LENGTH = 30

    fun normalize(input: String): String = input.trim().lowercase()

    /** 3 to 30 of `a-z 0-9 -`, with no hyphen at either end or two in a row. Expects a [normalize]d name. */
    fun isValid(slug: String): Boolean = slug.length in MIN_LENGTH..MAX_LENGTH && VALID.matches(slug)
}
