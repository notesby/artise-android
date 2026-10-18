/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.enterprise

import androidx.compose.ui.graphics.Color
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

/**
 * The only server Artise signs in to. Its `.well-known/matrix/client` points at [ARTISE_BASE_URL].
 */
const val ARTISE_SERVER_NAME = "artise.co"
const val ARTISE_BASE_URL = "https://matrix.artise.co"

/** Artise's own Sygnal, which holds the credentials for the Artise Firebase project. */
const val ARTISE_PUSH_GATEWAY = "$ARTISE_BASE_URL/_matrix/push/v1/notify"

/** Artise blue, `--blue` on the artise.co site. */
val ARTISE_BRAND_COLOR = Color(0xFF22408A)

val artiseAccountProvider = AccountProvider.Managed(
    serverName = ARTISE_SERVER_NAME,
    baseUrl = ARTISE_BASE_URL,
    canUseServerName = true,
)

/**
 * Replaces Element's FOSS [EnterpriseService] so the app is locked to artise.co, uses the Artise brand color
 * and never sends bug reports to Element.
 *
 * Firebase pushes go through Artise's own Sygnal; UnifiedPush keeps the app default gateway.
 * Only this service is replaced; the rest of `:features:enterprise:impl-foss` is still used as is.
 */
@ContributesBinding(AppScope::class, replaces = [DefaultEnterpriseService::class])
class ArtiseEnterpriseService : EnterpriseService {
    override suspend fun isEnterpriseUser(sessionId: SessionId) = false
    override suspend fun tweakMasUrl(url: String, urlContentFetcher: ClientUrlContentFetcher) = url
    override fun accountProviderAllowList(): List<AccountProvider> = listOf(artiseAccountProvider)
    override fun canConnectToAnyAccountProvider(): Boolean = false
    override suspend fun isAllowedToConnectToAccountProvider(accountProvider: AccountProvider) = artiseAccountProvider.matches(accountProvider)

    // Artise is not an Element Pro deployment, so there is nothing to fetch.
    override suspend fun isElementProEnforced(serverName: String): Boolean = false

    override suspend fun overrideBrandColor(sessionId: SessionId?, brandColor: String?) = Unit

    override fun brandColorsFlow(sessionId: SessionId?): Flow<Color?> = flowOf(ARTISE_BRAND_COLOR)

    override fun semanticColorsFlow(sessionId: SessionId?): Flow<SemanticColorsLightDark> = flowOf(SemanticColorsLightDark.default)

    override fun firebasePushGateway(): String = ARTISE_PUSH_GATEWAY
    override fun unifiedPushDefaultPushGateway(): String? = null

    override fun bugReportUrlFlow(sessionId: SessionId?): Flow<BugReportUrl> = flowOf(BugReportUrl.Disabled)

    override fun getNoisyNotificationChannelId(sessionId: SessionId): String? = null
}
