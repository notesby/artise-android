/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl

import co.artise.android.workspace.api.WorkspaceSlug
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkspaceSlugTest {
    /** What people type is trimmed and lowercased before it's checked or sent. */
    @Test
    fun `input is trimmed and lowercased`() {
        assertThat(WorkspaceSlug.normalize("  Acme ")).isEqualTo("acme")
    }

    /** 3 to 30 of a-z, 0-9 and single hyphens inside. */
    @Test
    fun `valid names`() {
        listOf("acme", "abc", "acme-labs", "a1-b2-c3", "x".repeat(30)).forEach { assertThat(WorkspaceSlug.isValid(it)).isTrue() }
    }

    /** Too short or long, a hyphen at either end or two in a row, or anything else: invalid, and never sent. */
    @Test
    fun `invalid names`() {
        listOf("ab", "", "x".repeat(31), "-acme", "acme-", "ac--me", "ac me", "acmé", "ACME", "acme_labs", "acme.co")
            .forEach { assertThat(WorkspaceSlug.isValid(it)).isFalse() }
    }
}
