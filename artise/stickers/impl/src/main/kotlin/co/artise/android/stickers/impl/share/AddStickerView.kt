/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import co.artise.android.stickers.impl.R
import co.artise.android.stickers.impl.maker.MadeSticker
import co.artise.android.stickers.impl.packs.StickerPicture
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.ui.strings.CommonStrings
import co.artise.android.stickers.api.R as StickersApiR

/** "Add to my stickers", opened from another app's share menu. */
@Composable
fun AddStickerView(state: AddStickerState, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ElementTheme.colors.bgCanvasDefault)
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(StickersApiR.string.screen_stickers_add_title), style = ElementTheme.typography.fontHeadingMdBold)
        when (val step = state.step) {
            AddStickerStep.Preparing, AddStickerStep.Saved -> CircularProgressIndicator()
            AddStickerStep.SignedOut -> Message(stringResource(StickersApiR.string.screen_stickers_add_signed_out), onClose)
            AddStickerStep.Failed -> Message(stringResource(StickersApiR.string.screen_stickers_add_failed), onClose)
            is AddStickerStep.Ready -> {
                AsyncImage(
                    model = step.made.picture.bytes,
                    contentDescription = stringResource(R.string.screen_stickers_preview_title),
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(ElementTheme.colors.bgSubtleSecondary),
                )
                if (!step.made.isCutOut) Note(stringResource(R.string.screen_stickers_not_cut_out))
                if (state.saveFailed) Note(stringResource(R.string.screen_stickers_save_failed), isError = true)
                Button(
                    text = stringResource(R.string.screen_stickers_save),
                    showProgress = step.isSaving,
                    enabled = !step.isSaving,
                    onClick = { state.eventSink(AddStickerEvent.Save) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    text = stringResource(CommonStrings.action_cancel),
                    enabled = !step.isSaving,
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Message(text: String, onClose: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Note(text)
        Button(text = stringResource(CommonStrings.action_close), onClick = onClose, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Note(text: String, isError: Boolean = false) {
    Text(
        text = text,
        style = ElementTheme.typography.fontBodyMdRegular,
        color = if (isError) ElementTheme.colors.textCriticalPrimary else ElementTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
    )
}

class AddStickerStatePreviewParam : PreviewParameterProvider<AddStickerState> {
    override val values = sequenceOf(
        AddStickerState(AddStickerStep.Preparing, saveFailed = false, eventSink = {}),
        AddStickerState(AddStickerStep.SignedOut, saveFailed = false, eventSink = {}),
        AddStickerState(
            AddStickerStep.Ready(MadeSticker(StickerPicture(byteArrayOf(), 1, 1, "image/webp"), isCutOut = false), isSaving = false),
            saveFailed = true,
            eventSink = {},
        ),
    )
}

@PreviewsDayNight
@Composable
internal fun AddStickerViewPreview(@PreviewParameter(AddStickerStatePreviewParam::class) state: AddStickerState) = ElementPreview {
    AddStickerView(state = state, onClose = {})
}
