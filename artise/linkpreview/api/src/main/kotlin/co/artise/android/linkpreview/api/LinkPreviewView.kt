/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.linkpreview.api

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.ui.media.MediaRequestData

// Pictures at least this wide show across the card; smaller ones (logos, icons) show as a thumbnail.
private const val WIDE_IMAGE_MIN_WIDTH = 400

/** A received link's preview, under the message. A tap opens the link. */
@Composable
fun LinkPreviewView(preview: LinkPreview, onClick: (url: String) -> Unit, modifier: Modifier = Modifier) {
    val image = preview.image
    val isWide = image != null && image.width != null && image.width >= WIDE_IMAGE_MIN_WIDTH
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ElementTheme.colors.bgSubtleSecondary)
            .clickable { onClick(preview.url) },
    ) {
        if (image != null && isWide) {
            AsyncImage(
                model = MediaRequestData(image.source, MediaRequestData.Kind.Content),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
            )
        }
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (image != null && !isWide) {
                AsyncImage(
                    model = MediaRequestData(image.source, MediaRequestData.Kind.Content),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
            LinkPreviewTexts(
                url = preview.url,
                siteName = preview.siteName,
                title = preview.title,
                description = preview.description,
                descriptionLines = 3,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun LinkPreviewViewPreview() = ElementPreview {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinkPreviewView(
            preview = LinkPreview(
                url = "https://www.example.com/recetas/mole",
                title = "Mole poblano, paso a paso",
                description = "La receta de la abuela, con todos los chiles y el chocolate, explicada para principiantes.",
                siteName = "Cocina de casa",
                image = LinkPreviewImage(MediaSource("mxc://example.com/a"), width = 1200, height = 630),
            ),
            onClick = {},
        )
        LinkPreviewView(
            preview = LinkPreview("https://example.com", title = "Example Domain", description = null, siteName = null, image = null),
            onClick = {},
        )
    }
}
