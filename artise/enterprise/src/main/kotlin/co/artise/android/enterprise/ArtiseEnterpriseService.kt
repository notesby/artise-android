/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.enterprise

import androidx.compose.ui.graphics.Color
import co.artise.android.workspace.api.SignInWorkspaceHolder
import co.artise.android.workspace.api.WorkspaceConfig
import co.artise.android.workspace.api.WorkspaceConfigRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.compound.colors.SemanticColorsLightDark
import io.element.android.features.enterprise.api.BugReportUrl
import io.element.android.features.enterprise.api.EnterpriseService
import io.element.android.features.enterprise.impl.DefaultEnterpriseService
import io.element.android.libraries.matrix.api.ClientUrlContentFetcher
import io.element.android.libraries.matrix.api.accountprovider.AccountProvider
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Artise's own workspace ("artise"), where accounts made before workspaces live. */
const val ARTISE_SERVER_NAME = "artise.co"

/** Artise blue, `--blue` on the artise.co site. */
val ARTISE_BRAND_COLOR = Color(0xFF22408A)

/**
 * Replaces Element's FOSS [EnterpriseService] so the app signs in only to Artise workspaces, uses the Artise brand color
 * and never sends bug reports to Element.
 *
 * There is no fixed server: sign-in starts with the workspace's name, which leads to its server (see artise/workspace). Element's
 * own server screens are never shown, and only the workspace chosen there (or artise.co) is accepted.
 *
 * Firebase pushes go through the account's workspace gateway (Artise's shared one by default); UnifiedPush keeps the app default.
 * Only this service is replaced; the rest of `:features:enterprise:impl-foss` is still used as is.
 */
@ContributesBinding(AppScope::class, replaces = [DefaultEnterpriseService::class])
class ArtiseEnterpriseService(
    private val workspaceConfigRepository: WorkspaceConfigRepository,
    private val signInWorkspaceHolder: SignInWorkspaceHolder,
) : EnterpriseService {
    override suspend fun isEnterpriseUser(sessionId: SessionId) = false
    override suspend fun tweakMasUrl(url: String, urlContentFetcher: ClientUrlContentFetcher) = url
    override fun accountProviderAllowList(): List<AccountProvider> = emptyList()
    override fun canConnectToAnyAccountProvider(): Boolean = false
    override suspend fun isAllowedToConnectToAccountProvider(accountProvider: AccountProvider): Boolean {
        val allowed = listOfNotNull(ARTISE_SERVER_NAME, signInWorkspaceHolder.current.value?.serverName)
        return allowed.any { AccountProvider.Generic(it).matches(accountProvider) }
    }

    // Artise is not an Element Pro deployment, so there is nothing to fetch.
    override suspend fun isElementProEnforced(serverName: String): Boolean = false

    override suspend fun overrideBrandColor(sessionId: SessionId?, brandColor: String?) = Unit

    override fun brandColorsFlow(sessionId: SessionId?): Flow<Color?> = flowOf(ARTISE_BRAND_COLOR)

    override fun semanticColorsFlow(sessionId: SessionId?): Flow<SemanticColorsLightDark> = flowOf(SemanticColorsLightDark.default)

    override fun firebasePushGateway(sessionId: SessionId): String = WorkspaceConfig.pushGatewayFor(workspaceConfigRepository.cached(sessionId))
    override fun unifiedPushDefaultPushGateway(): String? = null

    override fun bugReportUrlFlow(sessionId: SessionId?): Flow<BugReportUrl> = flowOf(BugReportUrl.Disabled)

    override fun getNoisyNotificationChannelId(sessionId: SessionId): String? = null
}
