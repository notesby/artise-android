/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.api

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Text

/** The card at the top of the chat list: the release's changes and "Got it", which dismisses it for this edition. */
@Composable
fun ArtiseWhatsNewCard(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(ElementTheme.colors.bgSubtleSecondary, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.screen_artise_whats_new_title),
            style = ElementTheme.typography.fontBodyLgMedium,
            color = ElementTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        listOf(R.string.screen_artise_whats_new_1, R.string.screen_artise_whats_new_2, R.string.screen_artise_whats_new_3).forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = CompoundIcons.Check(),
                    contentDescription = null,
                    tint = ElementTheme.colors.iconAccentPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Text(text = stringResource(item), style = ElementTheme.typography.fontBodyMdRegular, color = ElementTheme.colors.textSecondary)
            }
        }
        OutlinedButton(
            text = stringResource(R.string.screen_artise_whats_new_done),
            onClick = {
                ArtiseWhatsNew.dismiss(context)
                onDismiss()
            },
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
