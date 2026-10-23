/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.api

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

/**
 * "What's new": one card at the top of the chat list after an update, with the release's few changes, until it's
 * dismissed. A fresh install doesn't see it. Change [EDITION] and the `screen_artise_whats_new_*` texts per release
 * (iOS has the same card and edition).
 */
object ArtiseWhatsNew {
    /** This release's changes; bumped when the texts change. */
    const val EDITION = "2026-10-26"

    private const val PREFERENCES = "artise_whats_new"
    private const val SEEN = "seen"

    fun isShowing(context: Context): Boolean {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val seen = preferences.getString(SEEN, null)
        if (seen != null) return seen != EDITION
        // Never seen one: an update from before the card shows it; a fresh install counts it as seen.
        val isFreshInstall = try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.firstInstallTime == info.lastUpdateTime
        } catch (e: PackageManager.NameNotFoundException) {
            true
        }
        if (isFreshInstall) preferences.edit { putString(SEEN, EDITION) }
        return !isFreshInstall
    }

    fun dismiss(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit { putString(SEEN, EDITION) }
    }
}

/** Whether the card shows, read once; set it to `false` when it's dismissed. */
@Composable
fun rememberArtiseWhatsNew(): MutableState<Boolean> {
    val context = LocalContext.current
    return remember { mutableStateOf(ArtiseWhatsNew.isShowing(context)) }
}
