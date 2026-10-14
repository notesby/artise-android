/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.test

import co.artise.android.workspace.api.WorkspaceConfig
import co.artise.android.workspace.api.WorkspaceConfigRepository
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeWorkspaceConfigRepository(
    configs: Map<SessionId, WorkspaceConfig> = emptyMap(),
    private val fetchResult: (serverName: String) -> WorkspaceConfig? = { null },
) : WorkspaceConfigRepository {
    private val saved = MutableStateFlow(configs)
    val refreshed = mutableListOf<SessionId>()

    fun givenConfig(sessionId: SessionId, config: WorkspaceConfig?) {
        saved.value = if (config == null) saved.value - sessionId else saved.value + (sessionId to config)
    }

    override fun cached(sessionId: SessionId): WorkspaceConfig? = saved.value[sessionId]

    override fun configFlow(sessionId: SessionId): Flow<WorkspaceConfig?> = saved.map { it[sessionId] }

    override suspend fun refresh(sessionId: SessionId, force: Boolean) {
        refreshed += sessionId
    }

    override suspend fun fetch(serverName: String): WorkspaceConfig? = fetchResult(serverName)

    override fun clear(sessionId: SessionId) = givenConfig(sessionId, null)
}
