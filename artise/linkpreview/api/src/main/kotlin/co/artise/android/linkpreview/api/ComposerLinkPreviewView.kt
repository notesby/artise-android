/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.linkpreview.api

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.ui.strings.CommonStrings

/** Above the composer: the preview that will go with the message, with an X to send the link without it. */
@Composable
fun ComposerLinkPreviewView(state: ComposerLinkPreview, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val preview = state.preview
    Row(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ElementTheme.colors.bgSubtleSecondary)
            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            preview == null -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            preview.image != null -> AsyncImage(
                model = preview.image.file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            else -> Icon(CompoundIcons.Link(), contentDescription = null, tint = ElementTheme.colors.iconSecondary)
        }
        LinkPreviewTexts(
            url = state.url,
            siteName = preview?.siteName,
            title = preview?.title,
            description = preview?.description,
            descriptionLines = 1,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(CompoundIcons.Close(), contentDescription = stringResource(CommonStrings.action_remove))
        }
    }
}

@PreviewsDayNight
@Composable
internal fun ComposerLinkPreviewViewPreview() = ElementPreview {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ComposerLinkPreviewView(
            state = ComposerLinkPreview(
                url = "https://www.example.com/recetas/mole",
                preview = DraftLinkPreview(
                    "https://www.example.com/recetas/mole",
                    "Mole poblano, paso a paso",
                    "La receta de la abuela",
                    "Cocina de casa",
                    null
                ),
            ),
            onDismiss = {},
        )
        ComposerLinkPreviewView(state = ComposerLinkPreview("https://www.example.com/recetas/mole", preview = null), onDismiss = {})
    }
}
