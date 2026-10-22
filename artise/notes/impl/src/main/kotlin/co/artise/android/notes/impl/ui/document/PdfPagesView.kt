/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.document

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import kotlin.math.roundToInt

/**
 * A PDF's pages one under the other, each drawn at the screen's width when it comes into view.
 * [overlay] draws on top of a page (the page's index from 0), for highlights.
 */
@Composable
internal fun PdfPagesView(
    document: PdfDocument,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    overlay: @Composable BoxScope.(page: Int) -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(document.pageCount) { index ->
                PdfPageItem(document, index, widthPx, overlay)
            }
        }
    }
}

@Composable
private fun PdfPageItem(document: PdfDocument, index: Int, widthPx: Int, overlay: @Composable BoxScope.(page: Int) -> Unit) {
    val ratio = document.aspectRatio(index)
    val bitmap by produceState<Bitmap?>(initialValue = null, document, index, widthPx) {
        value = if (widthPx > 0) document.render(index, widthPx) else null
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .background(androidx.compose.ui.graphics.Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val page = bitmap
        if (page != null) {
            Image(
                bitmap = page.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(2.dp), color = ElementTheme.colors.iconSecondary)
        }
        overlay(index)
    }
}

/** Opens [file] with Android's renderer, which can only be used from one thread at a time, and draws pages on request. */
internal class PdfDocument private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {
    private val lock = Mutex()
    private val sizes = HashMap<Int, Pair<Int, Int>>()

    val pageCount: Int get() = renderer.pageCount

    /** Width over height, from the page's own size in points. */
    fun aspectRatio(index: Int): Float {
        val (width, height) = sizes.getOrPut(index) { renderer.openPage(index).use { it.width to it.height } }
        return if (height == 0) 1f else width.toFloat() / height
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap = withContext(Dispatchers.IO) {
        lock.withLock {
            renderer.openPage(index).use { page ->
                val scale = widthPx / page.width.toFloat()
                val bitmap = Bitmap.createBitmap(widthPx, (page.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    override fun close() {
        renderer.close()
        descriptor.close()
    }

    companion object {
        /** Null when the file isn't a PDF Android can read. */
        fun open(file: File): PdfDocument? = runCatchingExceptions {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfDocument(descriptor, PdfRenderer(descriptor))
        }.getOrNull()
    }
}

/** The document for [file], closed when it leaves the screen; null when it can't be opened. */
@Composable
internal fun rememberPdfDocument(file: String): PdfDocument? {
    val document = remember(file) { PdfDocument.open(File(file)) }
    DisposableEffect(document) { onDispose { document?.close() } }
    return document
}
