/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.enterprise

import app.cash.turbine.test
import co.artise.android.workspace.api.SignInWorkspace
import co.artise.android.workspace.api.WorkspaceConfig
import co.artise.android.workspace.test.FakeSignInWorkspaceHolder
import co.artise.android.workspace.test.FakeWorkspaceConfigRepository
import com.google.common.truth.Truth.assertThat
import io.element.android.features.enterprise.api.BugReportUrl
import io.element.android.libraries.matrix.api.accountprovider.AccountProvider
import io.element.android.libraries.matrix.test.A_SESSION_ID
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ArtiseEnterpriseServiceTest {
    private val workspaces = FakeWorkspaceConfigRepository()
    private val signIn = FakeSignInWorkspaceHolder()
    private val service = ArtiseEnterpriseService(workspaces, signIn)

    /** No server is offered or typed in Element's screens: sign-in starts with the workspace's name. */
    @Test
    fun `no fixed server`() {
        assertThat(service.accountProviderAllowList()).isEmpty()
        assertThat(service.canConnectToAnyAccountProvider()).isFalse()
    }

    /** artise.co, and the workspace chosen at sign-in, are accepted by server name or URL, in any case; others are refused. */
    @Test
    fun `only artise co and the chosen workspace are allowed`() = runTest {
        assertThat(service.isAllowedToConnectToAccountProvider(AccountProvider.Generic("Artise.co"))).isTrue()
        assertThat(service.isAllowedToConnectToAccountProvider(AccountProvider.Generic("https://artise.co/"))).isTrue()
        assertThat(service.isAllowedToConnectToAccountProvider(AccountProvider.Generic("acme.artise.co"))).isFalse()
        signIn.choose(SignInWorkspace("acme", "Acme", "acme.artise.co", logoUrl = null))
        assertThat(service.isAllowedToConnectToAccountProvider(AccountProvider.Generic("acme.artise.co"))).isTrue()
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

    /** Firebase pushes go through Artise's shared gateway, unless the account's workspace names its own; UnifiedPush keeps the app default. */
    @Test
    fun `firebase pushes use the workspace gateway`() {
        assertThat(service.firebasePushGateway(A_SESSION_ID)).isEqualTo("https://push.artise.co/_matrix/push/v1/notify")
        workspaces.givenConfig(A_SESSION_ID, WorkspaceConfig(pushGateway = "https://push.acme.example/_matrix/push/v1/notify"))
        assertThat(service.firebasePushGateway(A_SESSION_ID)).isEqualTo("https://push.acme.example/_matrix/push/v1/notify")
        assertThat(service.unifiedPushDefaultPushGateway()).isNull()
    }
}
