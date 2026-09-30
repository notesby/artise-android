/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.accountdeletion

import com.google.common.truth.Truth.assertThat
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import org.junit.Test

class DefaultAccountDeletionTest {
    private val store = object : AccountDeletionStore {
        override var startedAt: Long? = null
    }
    private val clock = FakeSystemClock()
    private val deletion = DefaultAccountDeletion(store, clock)

    /** The page opens in Spanish when the app is in Spanish, in English otherwise. */
    @Test
    fun `page language`() {
        assertThat(deletion.pageUrl("es")).isEqualTo("https://chat.artise.co/account/delete?lang=es")
        assertThat(deletion.pageUrl("en")).isEqualTo("https://chat.artise.co/account/delete")
        assertThat(deletion.pageUrl("fr")).isEqualTo("https://chat.artise.co/account/delete")
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
