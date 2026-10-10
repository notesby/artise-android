/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.history

import co.artise.android.notes.api.ChangeAuthor
import co.artise.android.notes.impl.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChangeLabelsTest {
    private fun label(how: String?, who: String? = "Ana", detail: String = "", by: String = "Ana") = ChangeLabels.of(ChangeAuthor(who, how, detail, by))

    /** Each way a change is made gets its own words, with the person and, for automations, which one. */
    @Test
    fun `every how has its label`() {
        assertThat(label("ari")).isEqualTo(ChangeLabel.Text(R.string.screen_notes_change_ari, listOf("Ana")))
        assertThat(label("automation", detail = "#3")).isEqualTo(ChangeLabel.Text(R.string.screen_notes_change_automation, listOf("Ana", "#3")))
        assertThat(label("routine", detail = "#1")).isEqualTo(ChangeLabel.Text(R.string.screen_notes_change_routine, listOf("Ana", "#1")))
        assertThat(label("brief")).isEqualTo(ChangeLabel.Text(R.string.screen_notes_change_brief, listOf("Ana", "")))
        assertThat(label("reaction")).isEqualTo(ChangeLabel.Text(R.string.screen_notes_change_reaction, listOf("Ana")))
        assertThat(label("chat")).isEqualTo(ChangeLabel.Text(R.string.screen_notes_change_chat, listOf("Ana")))
        assertThat(label("app")).isEqualTo(ChangeLabel.Text(R.string.screen_notes_change_app, listOf("Ana")))
        assertThat(label("files")).isEqualTo(ChangeLabel.Text(R.string.screen_notes_change_files, listOf("Ana")))
    }

    /** Ari on its own and synced devices name nobody. */
    @Test
    fun `ari_own and sync need no person`() {
        assertThat(label("ari_own", who = null, by = "Ari")).isEqualTo(ChangeLabel.Text(R.string.screen_notes_change_ari_own))
        assertThat(label("sync", who = null, by = "synced device")).isEqualTo(ChangeLabel.Text(R.string.screen_notes_change_sync))
    }

    /** An unknown how (a newer server), no how (an older one), or a label that needs a person the server didn't name: the plain `by`. */
    @Test
    fun `falls back to by`() {
        assertThat(label("telepathy", by = "Ana")).isEqualTo(ChangeLabel.Plain("Ana"))
        assertThat(label(null, by = "Luis")).isEqualTo(ChangeLabel.Plain("Luis"))
        assertThat(label("app", who = null, by = "synced device")).isEqualTo(ChangeLabel.Plain("synced device"))
        assertThat(label("ari", who = "  ", by = "Ari")).isEqualTo(ChangeLabel.Plain("Ari"))
    }

    /** Without a detail, the space left where "#3" goes is tidied away. */
    @Test
    fun `tidy removes the gap of a missing detail`() {
        assertThat(ChangeLabels.tidy("Ari, Ana's automation ")).isEqualTo("Ari, Ana's automation")
        assertThat(ChangeLabels.tidy("Ari, automatización  de Ana")).isEqualTo("Ari, automatización de Ana")
    }
}
