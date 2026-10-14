/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.api

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * A workspace's logo above a title and subtitle, like Element's icon-title-subtitle header. Without a logo, or while it loads or if it
 * fails, the plain company icon is shown instead.
 */
@Composable
fun WorkspaceHeader(
    title: String,
    subtitle: String?,
    logoUrl: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val fallback = @Composable { BigIcon(style = BigIcon.Style.Default(CompoundIcons.Company())) }
        if (logoUrl == null) {
            fallback()
        } else {
            SubcomposeAsyncImage(
                model = logoUrl,
                contentDescription = null,
                modifier = Modifier.size(LOGO_SIZE),
                loading = { fallback() },
                error = { fallback() },
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = ElementTheme.typography.fontHeadingMdBold,
            color = ElementTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val LOGO_SIZE = 64.dp
