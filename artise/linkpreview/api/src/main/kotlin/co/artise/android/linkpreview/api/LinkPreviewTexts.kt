/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.linkpreview.api

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.designsystem.theme.components.Text
import java.net.URI

@Composable
internal fun LinkPreviewTexts(
    url: String,
    siteName: String?,
    title: String?,
    description: String?,
    descriptionLines: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = siteName ?: hostOf(url),
            style = ElementTheme.typography.fontBodySmMedium,
            color = ElementTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (title != null) {
            Text(
                text = title,
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (description != null) {
            Text(
                text = description,
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
                maxLines = descriptionLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "https://www.example.com/a" → "example.com". */
private fun hostOf(url: String): String = runCatchingExceptions { URI(url).host }.getOrNull()?.removePrefix("www.") ?: url
