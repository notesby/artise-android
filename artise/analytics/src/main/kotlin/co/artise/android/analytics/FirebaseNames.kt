/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.analytics

/**
 * Firebase's rules for names and values: letters, digits and underscores, starting with a letter, 40 characters for
 * events and parameters, 24 for user properties, 100 for text values (36 for user properties). Some names are
 * Firebase's own and can't be used.
 */
internal object FirebaseNames {
    private const val MAX_NAME = 40
    private const val MAX_USER_PROPERTY_NAME = 24
    private const val MAX_VALUE = 100
    private const val MAX_USER_PROPERTY_VALUE = 36
    private const val MAX_PARAMS = 25

    private val reservedEvents = setOf(
        "ad_activeview",
        "ad_click",
        "ad_exposure",
        "ad_query",
        "ad_reward",
        "adunit_exposure",
        "app_background",
        "app_clear_data",
        "app_exception",
        "app_remove",
        "app_store_refund",
        "app_store_subscription_cancel",
        "app_store_subscription_convert",
        "app_store_subscription_renew",
        "app_upgrade",
        "app_update",
        "error",
        "first_open",
        "first_visit",
        "in_app_purchase",
        "notification_dismiss",
        "notification_foreground",
        "notification_open",
        "notification_receive",
        "os_update",
        "session_start",
        "session_start_with_rollout",
        "user_engagement",
    )
    private val reservedPrefixes = listOf("firebase_", "google_", "ga_")

    /** "CallStarted" → "call_started"; a name Firebase keeps for itself gets "el_" in front. */
    fun event(name: String): String {
        val cleaned = clean(name, MAX_NAME)
        return if (cleaned in reservedEvents || reservedPrefixes.any { cleaned.startsWith(it) }) "el_$cleaned".take(MAX_NAME) else cleaned
    }

    /** Parameters as Firebase takes them: at most 25, numbers as numbers, everything else as short text. */
    fun params(properties: Map<String, Any?>): Map<String, Any> = properties.entries
        .mapNotNull { (key, value) -> value?.let { clean(key, MAX_NAME) to paramValue(it) } }
        .filterNot { (key, _) -> reservedPrefixes.any { key.startsWith(it) } }
        .take(MAX_PARAMS)
        .toMap()

    fun userProperty(name: String): String = clean(name, MAX_USER_PROPERTY_NAME)

    fun userPropertyValue(value: Any): String = valueText(value).take(MAX_USER_PROPERTY_VALUE)

    fun value(text: String): String = text.take(MAX_VALUE)

    private fun paramValue(value: Any): Any = when (value) {
        is Int -> value.toLong()
        is Long -> value
        is Float -> value.toDouble()
        is Double -> value
        else -> valueText(value).take(MAX_VALUE)
    }

    private fun valueText(value: Any): String = if (value is Enum<*>) value.name else value.toString()

    private fun clean(name: String, max: Int): String {
        val snake = name
            .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .replace(Regex("[^A-Za-z0-9_]"), "_")
            .lowercase()
            .trim('_')
        val startsWithLetter = if (snake.firstOrNull()?.isLetter() == true) snake else "e_$snake"
        return startsWithLetter.take(max)
    }
}
