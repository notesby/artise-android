/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Temporary files the camera app writes photos into, before they're copied into the upload queue. */
internal object CameraFiles {
    private const val FOLDER = "notes_camera"

    /**
     * A new, empty file the camera app may write to, shared through the app's FileProvider. Photos are named by date
     * and time ("foto-20260929-153012.jpg"), so two relatives' photos don't clash like "IMG_1234.jpg" would.
     * Earlier photos are removed: by now they've been copied into the upload queue.
     */
    fun newPhotoUri(context: Context): Uri {
        val folder = File(context.cacheDir, FOLDER).apply { mkdirs() }
        folder.listFiles()?.forEach { it.delete() }
        val name = "foto-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()) + ".jpg"
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(folder, name))
    }
}
