/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.analytics

import com.google.common.truth.Truth.assertThat
import im.vector.app.features.analytics.itf.VectorAnalyticsEvent
import im.vector.app.features.analytics.itf.VectorAnalyticsScreen
import im.vector.app.features.analytics.plan.Interaction
import im.vector.app.features.analytics.plan.SuperProperties
import org.junit.Test

class FirebaseAnalyticsProviderTest {
    private class RecordingClient : FirebaseAnalyticsClient {
        val calls = mutableListOf<String>()
        val events = mutableListOf<Pair<String, Map<String, Any>>>()

        override fun setCollectionEnabled(enabled: Boolean) {
            calls += "collection=$enabled"
        }

        override fun resetData() {
            calls += "reset"
        }

        override fun logEvent(name: String, params: Map<String, Any>) {
            events += name to params
        }

        override fun logScreen(screenName: String, params: Map<String, Any>) {
            events += "screen:$screenName" to params
        }

        override fun setUserProperty(name: String, value: String) {
            calls += "user:$name=$value"
        }
    }

    private val client = RecordingClient()
    private val provider = FirebaseAnalyticsProvider(client)

    private fun event(name: String, properties: Map<String, Any?>? = null) = object : VectorAnalyticsEvent {
        override fun getName() = name

        override fun getProperties() = properties
    }

    /** Saying yes turns collection on; saying no turns it off and drops what wasn't sent. */
    @Test
    fun `consent turns collection on and off`() {
        provider.init()
        provider.stop()
        assertThat(client.calls).containsExactly("collection=true", "collection=false", "reset").inOrder()
    }

    /** A button tap reads as the button's name; the platform, set once, goes with every event. */
    @Test
    fun `interactions are named after the button`() {
        provider.updateSuperProperties(SuperProperties(appPlatform = SuperProperties.AppPlatform.EXA))
        provider.capture(Interaction(interactionType = Interaction.InteractionType.Touch, name = Interaction.Name.MobileAllChatsFilterPeople))
        val (name, params) = client.events.single()
        assertThat(name).isEqualTo("ui_mobile_all_chats_filter_people")
        assertThat(params["interaction_type"]).isEqualTo("Touch")
        assertThat(params["app_platform"]).isEqualTo("EXA")
    }

    /** Names follow Firebase's rules: snake case, 40 characters, and Firebase's own names are avoided. */
    @Test
    fun `names follow firebase rules`() {
        provider.capture(event("Error", mapOf("domain" to "E2EE", "count" to 3, "firebase_x" to "no")))
        provider.capture(event("A".repeat(60)))
        assertThat(client.events[0].first).isEqualTo("el_error")
        assertThat(client.events[0].second).containsExactly("domain", "E2EE", "count", 3L)
        assertThat(client.events[1].first).hasLength(40)
    }

    /** Screens become Firebase screen views. */
    @Test
    fun `screens are screen views`() {
        provider.screen(object : VectorAnalyticsScreen {
            override fun getName() = "NotesFolder"

            override fun getProperties(): Map<String, Any>? = null
        })
        assertThat(client.events.single().first).isEqualTo("screen:NotesFolder")
    }

    /** Errors are counted by kind only: a message could contain names or text from a chat. */
    @Test
    fun `errors carry only their kind`() {
        provider.trackError(IllegalStateException("Mamá's secret recipe"))
        assertThat(client.events.single()).isEqualTo("app_error_caught" to mapOf("type" to "IllegalStateException"))
    }
}
