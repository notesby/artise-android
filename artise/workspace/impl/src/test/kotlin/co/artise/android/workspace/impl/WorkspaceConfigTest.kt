/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl

import co.artise.android.workspace.api.WorkspaceConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkspaceConfigTest {
    /** The workspace's gateway when it names an https one; Artise's shared gateway otherwise. */
    @Test
    fun `push gateway choice`() {
        val shared = "https://push.artise.co/_matrix/push/v1/notify"
        assertThat(WorkspaceConfig.pushGatewayFor(null)).isEqualTo(shared)
        assertThat(WorkspaceConfig.pushGatewayFor(WorkspaceConfig())).isEqualTo(shared)
        assertThat(WorkspaceConfig.pushGatewayFor(WorkspaceConfig(pushGateway = "http://push.acme.example/notify"))).isEqualTo(shared)
        assertThat(WorkspaceConfig.pushGatewayFor(WorkspaceConfig(pushGateway = "https://push.acme.example/_matrix/push/v1/notify")))
            .isEqualTo("https://push.acme.example/_matrix/push/v1/notify")
    }

    /** A screen is hidden only when the workspace lists its features and that one is missing. */
    @Test
    fun `features listed by the workspace`() {
        assertThat(WorkspaceConfig().hasFeature("notes")).isTrue()
        assertThat(WorkspaceConfig(features = setOf("tasks")).hasFeature("notes")).isFalse()
        assertThat(WorkspaceConfig(features = setOf("notes")).hasFeature("notes")).isTrue()
    }

    /** Welcome in the phone's language, else the workspace's, else none. */
    @Test
    fun `welcome language`() {
        val config = WorkspaceConfig(welcome = mapOf("en" to "Welcome", "es" to "Bienvenido"), language = "es")
        assertThat(config.welcomeFor("en")).isEqualTo("Welcome")
        assertThat(config.welcomeFor("pt")).isEqualTo("Bienvenido")
        assertThat(config.copy(language = null).welcomeFor("pt")).isNull()
    }
}
