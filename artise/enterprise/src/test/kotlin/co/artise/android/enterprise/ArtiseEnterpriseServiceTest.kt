/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.enterprise

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.features.enterprise.api.BugReportUrl
import io.element.android.libraries.matrix.api.accountprovider.AccountProvider
import io.element.android.libraries.matrix.test.A_SESSION_ID
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ArtiseEnterpriseServiceTest {
    private val service = ArtiseEnterpriseService()

    /** Sign-in offers artise.co and nothing else, and the user can't type another server. */
    @Test
    fun `only artise co is offered`() {
        assertThat(service.accountProviderAllowList()).containsExactly(artiseAccountProvider)
        assertThat(service.canConnectToAnyAccountProvider()).isFalse()
    }

    /** artise.co is accepted whether typed as the server name or the base URL, in any case. */
    @Test
    fun `artise co is allowed by name or base url`() = runTest {
        assertThat(service.isAllowedToConnectToAccountProvider(AccountProvider.Generic("Artise.co"))).isTrue()
        assertThat(service.isAllowedToConnectToAccountProvider(AccountProvider.Generic("https://artise.co/"))).isTrue()
    }

    /** Any other server, matrix.org included, is refused. */
    @Test
    fun `other servers are refused`() = runTest {
        assertThat(service.isAllowedToConnectToAccountProvider(AccountProvider.Generic("matrix.org"))).isFalse()
        assertThat(service.isAllowedToConnectToAccountProvider(AccountProvider.Generic("artise.co.evil.example"))).isFalse()
    }

    /** Artise never counts as an Element Pro deployment, so the app never tells users to install Element Pro. */
    @Test
    fun `element pro is never enforced`() = runTest {
        assertThat(service.isElementProEnforced(ARTISE_SERVER_NAME)).isFalse()
    }

    /** Notifications use the Artise blue. */
    @Test
    fun `brand color is artise blue`() = runTest {
        service.brandColorsFlow(A_SESSION_ID).test {
            assertThat(awaitItem()).isEqualTo(ARTISE_BRAND_COLOR)
            awaitComplete()
        }
    }

    /** Bug reports are off, so nothing is ever sent to Element's rageshake server. */
    @Test
    fun `bug reports are disabled`() = runTest {
        service.bugReportUrlFlow(A_SESSION_ID).test {
            assertThat(awaitItem()).isEqualTo(BugReportUrl.Disabled)
            awaitComplete()
        }
    }

    /** Firebase pushes go through Artise's Sygnal on matrix.artise.co; UnifiedPush keeps the app default. */
    @Test
    fun `firebase pushes use the artise gateway`() {
        assertThat(service.firebasePushGateway()).isEqualTo("https://matrix.artise.co/_matrix/push/v1/notify")
        assertThat(service.unifiedPushDefaultPushGateway()).isNull()
    }
}
