/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.analytics

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import im.vector.app.features.analytics.itf.VectorAnalyticsEvent
import im.vector.app.features.analytics.itf.VectorAnalyticsScreen
import im.vector.app.features.analytics.plan.Interaction
import im.vector.app.features.analytics.plan.SuperProperties
import im.vector.app.features.analytics.plan.UserProperties
import io.element.android.services.analyticsproviders.api.AnalyticsProvider
import io.element.android.services.analyticsproviders.api.AnalyticsTransaction

/**
 * Sends Element's usage events (screens and actions, never message or note content) to Google Analytics for Firebase.
 *
 * It only runs while the person has said yes on "Help improve Artise": Element's analytics service calls [init] on
 * consent and [stop] when it's withdrawn, and forwards nothing in between.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class)
class FirebaseAnalyticsProvider(
    private val client: FirebaseAnalyticsClient,
) : AnalyticsProvider {
    override val name = "Firebase"

    @Volatile
    private var superProperties: Map<String, Any?> = emptyMap()

    override fun init() {
        client.setCollectionEnabled(true)
    }

    override fun stop() {
        // Stops collecting and drops what wasn't sent yet, as the person just said no.
        client.setCollectionEnabled(false)
        client.resetData()
    }

    override fun capture(event: VectorAnalyticsEvent) {
        val properties = event.getProperties().orEmpty()
        // "Interaction" events are all different buttons: named after the button, they read better in Firebase.
        val interaction = (properties["name"] as? Interaction.Name)?.name ?: properties["name"] as? String
        val name = if (event.getName() == INTERACTION && interaction != null) "ui_$interaction" else event.getName()
        client.logEvent(FirebaseNames.event(name), FirebaseNames.params(superProperties + properties))
    }

    override fun screen(screen: VectorAnalyticsScreen) {
        client.logScreen(FirebaseNames.value(screen.getName()), FirebaseNames.params(superProperties + screen.getProperties().orEmpty()))
    }

    override fun updateUserProperties(userProperties: UserProperties) {
        userProperties.getProperties().orEmpty().forEach { (key, value) ->
            client.setUserProperty(FirebaseNames.userProperty(key), FirebaseNames.userPropertyValue(value))
        }
    }

    override fun updateSuperProperties(updatedProperties: SuperProperties) {
        superProperties = superProperties + updatedProperties.getProperties().orEmpty()
    }

    /** Only the kind of error: its message could contain names or text from a chat. */
    override fun trackError(throwable: Throwable) {
        client.logEvent("app_error_caught", FirebaseNames.params(mapOf("type" to (throwable::class.simpleName ?: "Unknown"))))
    }

    override fun startTransaction(name: String, operation: String?, description: String?): AnalyticsTransaction? = null

    private companion object {
        const val INTERACTION = "Interaction"
    }
}
