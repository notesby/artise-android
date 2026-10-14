/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.accountdeletion

import co.artise.android.workspace.api.WorkspaceConfig
import co.artise.android.workspace.api.WorkspaceLinks
import co.artise.android.workspace.test.FakeWorkspaceConfigRepository
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultAccountDeletionTest {
    private val store = object : AccountDeletionStore {
        override var startedAt: Long? = null
    }
    private val clock = FakeSystemClock()
    private val workspaces = FakeWorkspaceConfigRepository()
    private val deletion = DefaultAccountDeletion(store, clock, workspaces)
    private val acme = SessionId("@ana:acme.artise.co")

    /** The workspace's own page, in Spanish when the app is in Spanish, in English otherwise. */
    @Test
    fun `page of the workspace in the app's language`() {
        workspaces.givenConfig(acme, WorkspaceConfig(links = WorkspaceLinks(null, null, "https://web.acme.example/account/delete")))
        assertThat(deletion.pageUrl(acme, "es")).isEqualTo("https://web.acme.example/account/delete?lang=es")
        assertThat(deletion.pageUrl(acme, "en")).isEqualTo("https://web.acme.example/account/delete")
        workspaces.givenConfig(acme, WorkspaceConfig(links = WorkspaceLinks(null, null, "https://web.acme.example/delete?from=app")))
        assertThat(deletion.pageUrl(acme, "es")).isEqualTo("https://web.acme.example/delete?from=app&lang=es")
    }

    /** No `links` (no web host): no page and no guessing, except artise.co until its file has `links`. */
    @Test
    fun `no page without links`() = runTest {
        workspaces.givenConfig(acme, WorkspaceConfig(name = "Acme"))
        assertThat(deletion.pageUrl(acme, "en")).isNull()
        assertThat(deletion.hasPageFlow(acme).first()).isFalse()
        val artise = SessionId("@ana:artise.co")
        assertThat(deletion.pageUrl(artise, "es")).isEqualTo("https://chat.artise.co/account/delete?lang=es")
        assertThat(deletion.hasPageFlow(artise).first()).isTrue()
    }

    /** A sign-out soon after opening the page is the deletion, once. */
    @Test
    fun `a sign-out after opening the page is the deletion`() {
        deletion.markStarted()
        clock.epochMillisResult += 5 * 60 * 1000L
        assertThat(deletion.consumeStarted()).isTrue()
        assertThat(deletion.consumeStarted()).isFalse()
    }

    /** Hours later, a sign-out has another cause: the usual explanation shows. */
    @Test
    fun `an old start is forgotten`() {
        deletion.markStarted()
        clock.epochMillisResult += AccountDeletion.STARTED_VALID_MILLIS + 1
        assertThat(deletion.consumeStarted()).isFalse()
    }

    /** Without the page opened, a sign-out isn't taken for a deletion. */
    @Test
    fun `nothing started`() {
        assertThat(deletion.consumeStarted()).isFalse()
    }
}
