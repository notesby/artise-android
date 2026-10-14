/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.pushproviders.firebase

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.enterprise.api.EnterpriseService
import io.element.android.libraries.matrix.api.core.SessionId

interface FirebaseGatewayProvider {
    // Artise: per account, see EnterpriseService.firebasePushGateway.
    fun getFirebaseGateway(sessionId: SessionId): String
}

@ContributesBinding(AppScope::class)
class DefaultFirebaseGatewayProvider(
    private val enterpriseService: EnterpriseService,
) : FirebaseGatewayProvider {
    override fun getFirebaseGateway(sessionId: SessionId): String {
        return enterpriseService.firebasePushGateway(sessionId) ?: FirebaseConfig.PUSHER_HTTP_URL
    }
}
