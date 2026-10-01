/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl.maker

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume

/** Cuts the people and things out of a photo, leaving the background transparent. */
fun interface SubjectCutout {
    /** The photo with only its subjects left, or `null` when it can't (nothing found, or the tool isn't ready). */
    suspend fun cutOut(photo: Bitmap): Bitmap?
}

/** Google's on-device subject segmentation (ML Kit, through Google Play services): the photo never leaves the phone. */
@ContributesBinding(AppScope::class)
class DefaultSubjectCutout : SubjectCutout {
    private val segmenter by lazy {
        SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
    }

    override suspend fun cutOut(photo: Bitmap): Bitmap? = suspendCancellableCoroutine { continuation ->
        segmenter.process(InputImage.fromBitmap(photo, 0))
            .addOnSuccessListener { result -> continuation.resume(result.foregroundBitmap) }
            .addOnFailureListener { error ->
                // Usually Google Play services still downloading the model: the photo is used whole instead.
                Timber.w(error, "Stickers: couldn't cut out the photo")
                continuation.resume(null)
            }
    }
}
