/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.artiseid

import android.content.Context
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.os.Build
import co.artise.android.workspace.api.WorkspaceInvite
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.di.annotations.ApplicationContext

/**
 * Whether https://join.artise.co links open this app, so a sign-in that returns there (Artise ID, a workspace's own client) comes back.
 * True for the release app once Android has verified join.artise.co's assetlinks.json, and for any build the person (or adb, for
 * testing) allowed to open those links.
 */
fun interface JoinLinkCheck {
    fun opensJoinLinks(): Boolean
}

@ContributesBinding(AppScope::class)
class DefaultJoinLinkCheck(
    @ApplicationContext private val context: Context,
    private val buildMeta: BuildMeta,
) : JoinLinkCheck {
    override fun opensJoinLinks(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return buildMeta.applicationId == RELEASE_APPLICATION_ID
        val manager = context.getSystemService(DomainVerificationManager::class.java) ?: return false
        val state = runCatchingExceptions { manager.getDomainVerificationUserState(context.packageName) }.getOrNull() ?: return false
        if (!state.isLinkHandlingAllowed) return false
        return when (state.hostToStateMap[WorkspaceInvite.HOST]) {
            DomainVerificationUserState.DOMAIN_STATE_VERIFIED, DomainVerificationUserState.DOMAIN_STATE_SELECTED -> true
            else -> false
        }
    }

    companion object {
        /** The release app, the one join.artise.co's assetlinks.json names. */
        const val RELEASE_APPLICATION_ID = "co.artise.app"
    }
}
