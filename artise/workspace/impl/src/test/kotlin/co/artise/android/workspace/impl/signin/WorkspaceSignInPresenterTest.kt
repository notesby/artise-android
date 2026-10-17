/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.signin

import co.artise.android.workspace.api.SignInWorkspace
import co.artise.android.workspace.api.Workspace
import co.artise.android.workspace.api.WorkspaceConfig
import co.artise.android.workspace.api.WorkspaceInvite
import co.artise.android.workspace.api.WorkspaceLookup
import co.artise.android.workspace.api.WorkspaceLookupResult
import co.artise.android.workspace.api.WorkspaceSignInEntryPoint
import co.artise.android.workspace.test.FakeSignInWorkspaceHolder
import co.artise.android.workspace.test.FakeWorkspaceConfigRepository
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.auth.MatrixHomeServerDetails
import io.element.android.libraries.matrix.test.auth.FakeMatrixAuthenticationService
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WorkspaceSignInPresenterTest {
    private val acme = Workspace("acme", "Acme", "acme.artise.co", "https://matrix.acme.artise.co", isSuspended = false, joinUrl = null)
    private val homeservers = mutableListOf<String>()
    private val passwordServer = MatrixHomeServerDetails("https://matrix.acme.artise.co", supportsPasswordLogin = true, supportsOAuthLogin = false)

    private fun presenter(
        lookupResult: (String) -> WorkspaceLookupResult = { WorkspaceLookupResult.Found(acme) },
        homeserverResult: (String) -> Result<MatrixHomeServerDetails> = { Result.success(passwordServer) },
        config: WorkspaceConfig? = WorkspaceConfig(name = "Acme Inc.", logoUrl = "https://artise.co/brand/logo-1.png"),
        holder: FakeSignInWorkspaceHolder = FakeSignInWorkspaceHolder(),
        params: WorkspaceSignInEntryPoint.Params = WorkspaceSignInEntryPoint.Params(),
    ) = WorkspaceSignInPresenter(
        params = params,
        lookup = object : WorkspaceLookup {
            override suspend fun find(input: String) = lookupResult(input)
        },
        configRepository = FakeWorkspaceConfigRepository(fetchResult = { config }),
        authenticationService = FakeMatrixAuthenticationService(
            setHomeserverResult = {
                homeservers += it
                homeserverResult(it)
            },
        ),
        holder = holder,
    )

    /** Typing "acme" finds its server, sets it for sign-in, and moves on with the workspace's name and logo. */
    @Test
    fun `a workspace leads to its server`() = runTest {
        val holder = FakeSignInWorkspaceHolder()
        presenter(holder = holder).test {
            awaitItem().eventSink(WorkspaceSignInEvent.InputChanged("Acme"))
            awaitItem().eventSink(WorkspaceSignInEvent.Continue)
            val ready = consumeItemsUntilPredicate { it.readyServerName != null && !it.isLoading }.last()
            assertThat(ready.readyServerName).isEqualTo("acme.artise.co")
            assertThat(homeservers).containsExactly("acme.artise.co")
            assertThat(holder.current.value).isEqualTo(SignInWorkspace("acme", "Acme Inc.", "acme.artise.co", "https://artise.co/brand/logo-1.png"))
        }
    }

    /** Without branding (no section, or the file can't be read), sign-in still goes on, with the lookup's name. */
    @Test
    fun `branding never blocks sign-in`() = runTest {
        val holder = FakeSignInWorkspaceHolder()
        presenter(config = null, holder = holder).test {
            awaitItem().eventSink(WorkspaceSignInEvent.InputChanged("acme"))
            awaitItem().eventSink(WorkspaceSignInEvent.Continue)
            consumeItemsUntilPredicate { it.readyServerName != null && !it.isLoading }
            assertThat(holder.current.value?.name).isEqualTo("Acme")
            assertThat(holder.current.value?.logoUrl).isNull()
        }
    }

    /** Each lookup answer becomes its plain-words problem, and no server is set. */
    @Test
    fun `lookup problems`() = runTest {
        val cases = mapOf(
            WorkspaceLookupResult.Invalid to WorkspaceProblem.Invalid,
            WorkspaceLookupResult.NotFound("nonexistent-ws") to WorkspaceProblem.NotFound("nonexistent-ws"),
            WorkspaceLookupResult.Suspended(acme.copy(isSuspended = true)) to WorkspaceProblem.Paused,
            WorkspaceLookupResult.TooManyTries to WorkspaceProblem.TooManyTries,
            WorkspaceLookupResult.Unreachable to WorkspaceProblem.Unreachable,
        )
        cases.forEach { (answer, problem) ->
            presenter(lookupResult = { answer }).test {
                awaitItem().eventSink(WorkspaceSignInEvent.InputChanged("nonexistent-ws"))
                awaitItem().eventSink(WorkspaceSignInEvent.Continue)
                assertThat(consumeItemsUntilPredicate { !it.isLoading && it.problem != null }.last().problem).isEqualTo(problem)
            }
        }
        assertThat(homeservers).isEmpty()
    }

    /** A server name goes straight to that server. */
    @Test
    fun `a server name`() = runTest {
        presenter(lookupResult = { WorkspaceLookupResult.ServerName("artise.co") }, config = null).test {
            awaitItem().eventSink(WorkspaceSignInEvent.InputChanged("artise.co"))
            awaitItem().eventSink(WorkspaceSignInEvent.Continue)
            assertThat(consumeItemsUntilPredicate { it.readyServerName != null && !it.isLoading }.last().readyServerName).isEqualTo("artise.co")
        }
    }

    /** The server can't be reached, or doesn't take passwords: said with the workspace's name; trying again works. */
    @Test
    fun `server problems can be retried`() = runTest {
        var fail = true
        presenter(homeserverResult = { if (fail) Result.failure(IllegalStateException("down")) else Result.success(passwordServer) }).test {
            awaitItem().eventSink(WorkspaceSignInEvent.InputChanged("acme"))
            awaitItem().eventSink(WorkspaceSignInEvent.Continue)
            val failed = consumeItemsUntilPredicate { !it.isLoading && it.problem != null }.last()
            assertThat(failed.problem).isEqualTo(WorkspaceProblem.ServerUnreachable("Acme Inc."))
            fail = false
            failed.eventSink(WorkspaceSignInEvent.Continue)
            assertThat(consumeItemsUntilPredicate { it.readyServerName != null && !it.isLoading }.last().readyServerName).isEqualTo("acme.artise.co")
        }
        // A server with only its own sign-in page (MAS) goes on: the login step opens that page.
        presenter(homeserverResult = { Result.success(passwordServer.copy(supportsPasswordLogin = false, supportsOAuthLogin = true)) }).test {
            awaitItem().eventSink(WorkspaceSignInEvent.InputChanged("acme"))
            awaitItem().eventSink(WorkspaceSignInEvent.Continue)
            assertThat(consumeItemsUntilPredicate { it.readyServerName != null && !it.isLoading }.last().readyServerName).isEqualTo("acme.artise.co")
        }
        presenter(homeserverResult = { Result.success(passwordServer.copy(supportsPasswordLogin = false, supportsOAuthLogin = false)) }).test {
            awaitItem().eventSink(WorkspaceSignInEvent.InputChanged("acme"))
            awaitItem().eventSink(WorkspaceSignInEvent.Continue)
            assertThat(consumeItemsUntilPredicate { !it.isLoading && it.problem != null }.last().problem)
                .isEqualTo(WorkspaceProblem.NoPasswordSignIn("Acme Inc."))
        }
    }

    /** A workspace picked from the Artise ID list opens without a lookup: its server is set directly. */
    @Test
    fun `a workspace from the Artise ID list opens without a lookup`() = runTest {
        val holder = FakeSignInWorkspaceHolder()
        presenter(lookupResult = { error("no lookup expected") }, holder = holder).test {
            awaitItem().eventSink(WorkspaceSignInEvent.Open(slug = "acme", name = "Acme", serverName = "acme.artise.co"))
            assertThat(consumeItemsUntilPredicate { it.readyServerName != null && !it.isLoading }.last().readyServerName).isEqualTo("acme.artise.co")
            assertThat(homeservers).containsExactly("acme.artise.co")
            assertThat(holder.current.value).isEqualTo(SignInWorkspace("acme", "Acme Inc.", "acme.artise.co", "https://artise.co/brand/logo-1.png"))
        }
    }

    /** The field starts with the last workspace used. */
    @Test
    fun `prefill with the last workspace`() = runTest {
        presenter(holder = FakeSignInWorkspaceHolder(lastTyped = "artise")).test {
            assertThat(awaitItem().input).isEqualTo("artise")
        }
    }

    /**
     * An invite link: its workspace fills the field and is looked up, the join page opens; back from it, sign-in for that workspace
     * goes on by itself.
     */
    @Test
    fun `an invite opens its join page, then sign-in`() = runTest {
        val invite = WorkspaceInvite("acme", "Ab3_xyZ9")
        presenter(
            lookupResult = { WorkspaceLookupResult.Found(acme.copy(joinUrl = "https://join.artise.co/acme")) },
            holder = FakeSignInWorkspaceHolder(lastTyped = "artise"),
            params = WorkspaceSignInEntryPoint.Params(invite = invite),
        ).test {
            val opening = consumeItemsUntilPredicate { it.joinPageUrl != null }.last()
            assertThat(opening.input).isEqualTo("acme")
            assertThat(opening.joinPageUrl).isEqualTo("https://join.artise.co/acme/Ab3_xyZ9")
            // Resuming before the page was opened does nothing.
            opening.eventSink(WorkspaceSignInEvent.Resumed)
            opening.eventSink(WorkspaceSignInEvent.JoinPageOpened)
            assertThat(homeservers).isEmpty()
            consumeItemsUntilPredicate { it.joinPageUrl == null }.last().eventSink(WorkspaceSignInEvent.Resumed)
            assertThat(consumeItemsUntilPredicate { it.readyServerName != null && !it.isLoading }.last().readyServerName).isEqualTo("acme.artise.co")
        }
    }

    /** An invite to a workspace whose server has no join page yet, or that doesn't exist: said plainly, nothing opens. */
    @Test
    fun `invite problems`() = runTest {
        val invite = WorkspaceInvite("acme", "Ab3_xyZ9")
        presenter(params = WorkspaceSignInEntryPoint.Params(invite = invite)).test {
            val state = consumeItemsUntilPredicate { !it.isLoading && it.problem != null }.last()
            assertThat(state.problem).isEqualTo(WorkspaceProblem.InviteUnavailable)
            assertThat(state.joinPageUrl).isNull()
        }
        presenter(lookupResult = { WorkspaceLookupResult.NotFound("acme") }, params = WorkspaceSignInEntryPoint.Params(invite = invite)).test {
            assertThat(consumeItemsUntilPredicate { !it.isLoading && it.problem != null }.last().problem).isEqualTo(WorkspaceProblem.NotFound("acme"))
        }
    }
}
