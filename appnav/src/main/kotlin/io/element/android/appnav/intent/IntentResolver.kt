/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav.intent

import android.content.Intent
import co.artise.android.workspace.api.ArtiseIdCallback
import co.artise.android.workspace.api.WorkspaceInvite
import dev.zacsweers.metro.Inject
import io.element.android.features.login.api.LoginIntentResolver
import io.element.android.features.login.api.LoginParams
import io.element.android.features.share.api.ShareIntentData
import io.element.android.features.share.api.ShareIntentHandler
import io.element.android.libraries.deeplink.api.DeeplinkData
import io.element.android.libraries.deeplink.api.DeeplinkParser
import io.element.android.libraries.matrix.api.permalink.PermalinkData
import io.element.android.libraries.matrix.api.permalink.PermalinkParser
import io.element.android.libraries.oauth.api.OAuthAction
import io.element.android.libraries.oauth.api.OAuthIntentResolver
import timber.log.Timber

sealed interface ResolvedIntent {
    data class Navigation(val deeplinkData: DeeplinkData) : ResolvedIntent
    data class OAuth(val oAuthAction: OAuthAction) : ResolvedIntent
    data class Permalink(val permalinkData: PermalinkData) : ResolvedIntent
    data class Login(val params: LoginParams) : ResolvedIntent
    data class IncomingShare(val shareIntentData: ShareIntentData) : ResolvedIntent

    // Artise: an invite link to a workspace (https://join.artise.co/{slug}/{code}).
    data class WorkspaceInvite(val invite: co.artise.android.workspace.api.WorkspaceInvite) : ResolvedIntent

    // Artise: the return of an Artise ID sign-in (https://join.artise.co/oauth with its state).
    data class ArtiseIdReturn(val url: String) : ResolvedIntent
}

@Inject
class IntentResolver(
    private val deeplinkParser: DeeplinkParser,
    private val loginIntentResolver: LoginIntentResolver,
    private val oAuthIntentResolver: OAuthIntentResolver,
    private val permalinkParser: PermalinkParser,
    private val shareIntentHandler: ShareIntentHandler,
    private val artiseIdCallback: ArtiseIdCallback,
) {
    fun resolve(intent: Intent): ResolvedIntent? {
        if (intent.canBeIgnored()) return null

        // Coming from a notification?
        val deepLinkData = deeplinkParser.getFromIntent(intent)
        if (deepLinkData != null) return ResolvedIntent.Navigation(deepLinkData)

        // Artise: back from Artise ID? Checked before OAuth: a workspace's own client returns to the same address, told apart by state.
        val artiseIdReturn = intent.takeIf { it.action == Intent.ACTION_VIEW }?.dataString?.takeIf(artiseIdCallback::handles)
        if (artiseIdReturn != null) return ResolvedIntent.ArtiseIdReturn(artiseIdReturn)

        // Coming during login using OAuth?
        val oAuthAction = oAuthIntentResolver.resolve(intent)
        if (oAuthAction != null) return ResolvedIntent.OAuth(oAuthAction)

        val actionViewData = intent
            .takeIf { it.action == Intent.ACTION_VIEW }
            ?.dataString

        // Artise: an invite to a workspace?
        val workspaceInvite = actionViewData?.let { WorkspaceInvite.parse(it) }
        if (workspaceInvite != null) return ResolvedIntent.WorkspaceInvite(workspaceInvite)

        // Mobile configuration link clicked? (mobile.element.io)
        val mobileLoginData = actionViewData
            ?.let { loginIntentResolver.parse(it) }
        if (mobileLoginData != null) return ResolvedIntent.Login(mobileLoginData)

        // External link clicked? (matrix.to, element.io, etc.)
        val permalinkData = actionViewData
            ?.let { permalinkParser.parse(it) }
            ?.takeIf { it !is PermalinkData.FallbackLink }
        if (permalinkData != null) return ResolvedIntent.Permalink(permalinkData)

        if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val data = shareIntentHandler.handleIncomingShareIntent(intent) ?: return null
            return ResolvedIntent.IncomingShare(data)
        }

        // Unknown intent
        Timber.w("Unknown intent")
        return null
    }
}

private fun Intent.canBeIgnored(): Boolean {
    if (flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return true
    return action == Intent.ACTION_MAIN &&
        categories?.contains(Intent.CATEGORY_LAUNCHER) == true
}
