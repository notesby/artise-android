/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.api

/** An invite to a workspace, from a `https://join.artise.co/{slug}/{code}` link. */
data class WorkspaceInvite(val slug: String, val code: String) {
    companion object {
        const val HOST = "join.artise.co"
        private val CODE = Regex("^[A-Za-z0-9_-]{6,64}$")
        private val LINK = Regex("^https://${Regex.escape(HOST)}/([^/?#]+)/([^/?#]+)/?(?:[?#].*)?$", RegexOption.IGNORE_CASE)

        /** The invite in [link], or null when it isn't one (wrong host or shape, invalid workspace name or code). */
        fun parse(link: String): WorkspaceInvite? {
            val match = LINK.matchEntire(link.trim()) ?: return null
            val slug = WorkspaceSlug.normalize(match.groupValues[1]).takeIf(WorkspaceSlug::isValid) ?: return null
            val code = match.groupValues[2].takeIf { CODE.matches(it) } ?: return null
            return WorkspaceInvite(slug, code)
        }
    }
}
