/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl

import co.artise.android.workspace.api.WorkspaceInvite
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkspaceInviteTest {
    /** https://join.artise.co/{slug}/{code}: the workspace name and the code (6 to 64 of A-Z a-z 0-9 _ -). */
    @Test
    fun `invite links`() {
        assertThat(WorkspaceInvite.parse("https://join.artise.co/acme/Ab3_xyZ9")).isEqualTo(WorkspaceInvite("acme", "Ab3_xyZ9"))
        assertThat(WorkspaceInvite.parse("https://join.artise.co/Acme/abc-123/")).isEqualTo(WorkspaceInvite("acme", "abc-123"))
        assertThat(WorkspaceInvite.parse("https://join.artise.co/acme/abc123?utm=x")).isEqualTo(WorkspaceInvite("acme", "abc123"))
        assertThat(WorkspaceInvite.parse("https://join.artise.co/acme/" + "a".repeat(64))?.code).hasLength(64)
    }

    /** Anything else isn't an invite: another host, http, a bad workspace name, a short or odd code, more or fewer parts. */
    @Test
    fun `not invite links`() {
        listOf(
            "https://evil.example/acme/abc123",
            "http://join.artise.co/acme/abc123",
            "https://join.artise.co/ab/abc123",
            "https://join.artise.co/acme/abc",
            "https://join.artise.co/acme/abc123!",
            "https://join.artise.co/acme/" + "a".repeat(65),
            "https://join.artise.co/acme",
            "https://join.artise.co/acme/abc123/extra",
            "https://join.artise.co.evil.example/acme/abc123",
        ).forEach { assertThat(WorkspaceInvite.parse(it)).isNull() }
    }
}
