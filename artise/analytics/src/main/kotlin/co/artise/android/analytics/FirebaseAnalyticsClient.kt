/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.annotations.ApplicationContext

/** The few Firebase Analytics calls Artise makes, behind an interface so the naming rules can be tested on the JVM. */
interface FirebaseAnalyticsClient {
    fun setCollectionEnabled(enabled: Boolean)

    fun resetData()

    fun logEvent(name: String, params: Map<String, Any>)

    fun logScreen(screenName: String, params: Map<String, Any>)

    fun setUserProperty(name: String, value: String)
}

@ContributesBinding(AppScope::class)
class DefaultFirebaseAnalyticsClient(
    @ApplicationContext private val context: Context,
) : FirebaseAnalyticsClient {
    private val analytics by lazy { FirebaseAnalytics.getInstance(context) }

    override fun setCollectionEnabled(enabled: Boolean) {
        // Usage statistics only: nothing for ads, ever.
        analytics.setConsent(
            mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to
                    if (enabled) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED,
            )
        )
        analytics.setAnalyticsCollectionEnabled(enabled)
    }

    override fun resetData() = analytics.resetAnalyticsData()

    override fun logEvent(name: String, params: Map<String, Any>) = analytics.logEvent(name, params.toBundle())

    override fun logScreen(screenName: String, params: Map<String, Any>) = analytics.logEvent(
        FirebaseAnalytics.Event.SCREEN_VIEW,
        (params + mapOf(FirebaseAnalytics.Param.SCREEN_NAME to screenName, FirebaseAnalytics.Param.SCREEN_CLASS to screenName)).toBundle(),
    )

    override fun setUserProperty(name: String, value: String) = analytics.setUserProperty(name, value)

    private fun Map<String, Any>.toBundle() = Bundle().also { bundle ->
        forEach { (key, value) ->
            when (value) {
                is Long -> bundle.putLong(key, value)
                is Double -> bundle.putDouble(key, value)
                else -> bundle.putString(key, value.toString())
            }
        }
    }
}
