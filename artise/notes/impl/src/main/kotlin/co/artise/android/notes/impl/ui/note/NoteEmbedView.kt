/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.note

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import co.artise.android.notes.impl.R
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import java.io.File

/**
 * An embedded photo or file in a note. Photos show at full width once downloaded; files show as a chip.
 * Tapping either opens it in the app the person uses for that kind of file.
 */
@Composable
internal fun NoteEmbedView(
    target: String,
    embed: EmbedState?,
    onOpen: (path: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    when {
        embed == null -> FileChip(name = target, isImage = true, onClick = null, modifier = modifier)
        embed.isImage && embed.file != null -> AsyncImage(
            model = photoRequest(embed.file),
            contentDescription = embed.name,
            contentScale = ContentScale.FillWidth,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .clip(shape)
                .clickable { onOpen(embed.path) },
        )
        embed.isImage && !embed.failed -> Box(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .background(ElementTheme.colors.bgSubtleSecondary, shape),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        // A document, or a photo that couldn't be downloaded yet: tapping opens it (or tries again).
        else -> FileChip(
            name = embed.name,
            isImage = embed.isImage,
            onClick = { onOpen(embed.path) },
            modifier = modifier,
            size = embed.size,
            isDownloading = embed.isDownloading,
        )
    }
}

/**
 * A photo decoded in ordinary memory rather than on the graphics hardware: HDR photos (HEIC with a gain map, as many
 * phones take) can otherwise draw black.
 */
@Composable
internal fun photoRequest(file: String): ImageRequest =
    ImageRequest.Builder(LocalContext.current).data(File(file)).allowHardware(false).build()

/** A document (or a photo that couldn't load) as a card: its kind of file and size, a spinner while it downloads. */
@Composable
private fun FileChip(
    name: String,
    isImage: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: Long? = null,
    isDownloading: Boolean = false,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ElementTheme.colors.bgSubtleSecondary)
            .then(if (onClick != null && !isDownloading) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (isImage) CompoundIcons.Image() else CompoundIcons.Document(),
            contentDescription = null,
            tint = ElementTheme.colors.iconSecondary,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = ElementTheme.typography.fontBodyLgMedium,
                color = if (onClick != null) ElementTheme.colors.textPrimary else ElementTheme.colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val details = when {
                onClick == null -> stringResource(R.string.screen_notes_attachment_missing)
                else -> listOfNotNull(
                    name.substringAfterLast('.', "").uppercase().takeIf { it.isNotEmpty() && it.length <= 5 },
                    size?.let { Formatter.formatShortFileSize(context, it) },
                ).joinToString(" · ")
            }
            if (details.isNotEmpty()) {
                Text(details, style = ElementTheme.typography.fontBodySmRegular, color = ElementTheme.colors.textSecondary)
            }
        }
        if (isDownloading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}

/**
 * Hands a downloaded file to the app the person uses for that kind of file, through the app's FileProvider
 * (the file stays private; that app only gets to read it). Returns false when no app can open it.
 */
internal fun Context.openDownloadedFile(request: OpenFileRequest): Boolean {
    // Only the cache is shared with other apps. A photo still waiting to upload lives in app storage: copy it out first.
    val source = File(request.file)
    val file = if (source.canonicalPath.startsWith(cacheDir.canonicalPath)) {
        source
    } else {
        File(File(cacheDir, "notes_open").apply { mkdirs() }, request.name).also { source.copyTo(it, overwrite = true) }
    }
    val uri = try {
        FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    } catch (e: IllegalArgumentException) {
        return false
    }
    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        startActivity(Intent.createChooser(intent, request.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
