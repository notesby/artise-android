/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl.picker

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import co.artise.android.stickers.impl.R
import co.artise.android.stickers.impl.Sticker
import co.artise.android.stickers.impl.StickerSource
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.SegmentedButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.ui.media.MediaRequestData

/** The sticker picker, as a sheet over the chat. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPickerView(
    state: StickerPickerState,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, scrollable = false) {
        StickerPickerContent(state)
    }
    StickerPickerDialogs(state)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StickerPickerContent(state: StickerPickerState) {
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { state.eventSink(StickerPickerEvent.Make(it.toString())) }
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            StickerTab.entries.forEachIndexed { index, tab ->
                SegmentedButton(
                    index = index,
                    count = StickerTab.entries.size,
                    selected = state.tab == tab,
                    onClick = { state.eventSink(StickerPickerEvent.SelectTab(tab)) },
                    text = stringResource(if (tab == StickerTab.MINE) R.string.screen_stickers_mine else R.string.screen_stickers_starter),
                )
            }
        }
        val stickers = if (state.tab == StickerTab.MINE) state.mine else state.starter
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 76.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(360.dp),
        ) {
            if (state.tab == StickerTab.MINE) {
                item(key = "make") {
                    MakeTile(
                        isWorking = state.making == MakingState.Working,
                        onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    )
                }
            }
            items(stickers, key = { it.id }) { sticker ->
                StickerTile(
                    sticker = sticker,
                    isSending = state.sendingId == sticker.id,
                    isDimmed = state.sendingId != null && state.sendingId != sticker.id,
                    onClick = { state.eventSink(StickerPickerEvent.Send(sticker)) },
                    onLongClick = if (state.tab == StickerTab.MINE) {
                        { state.eventSink(StickerPickerEvent.StartRemove(sticker)) }
                    } else {
                        null
                    },
                )
            }
        }
        val footer = when {
            state.tab == StickerTab.MINE && state.mine.isEmpty() -> stringResource(R.string.screen_stickers_empty)
            state.tab == StickerTab.STARTER -> stringResource(R.string.screen_stickers_starter_credit)
            else -> null
        }
        footer?.let {
            Text(
                text = it,
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun MakeTile(isWorking: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(ElementTheme.colors.bgSubtleSecondary)
            .combinedClickable(enabled = !isWorking, onClickLabel = stringResource(R.string.screen_stickers_make), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isWorking) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Icon(imageVector = CompoundIcons.Plus(), contentDescription = stringResource(R.string.screen_stickers_make))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerTile(
    sticker: Sticker,
    isSending: Boolean,
    isDimmed: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClickLabel = sticker.description, onLongClick = onLongClick, onClick = onClick)
            .alpha(if (isDimmed) DIMMED_ALPHA else 1f),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = sticker.imageModel(),
            contentDescription = sticker.description,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
        )
        if (isSending) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

/** What Coil loads: a sticker on the server through Element's media loader, a starter sticker from the app. */
internal fun Sticker.imageModel(): Any = when (val source = source) {
    is StickerSource.Uploaded -> MediaRequestData(MediaSource(source.mxcUrl), MediaRequestData.Kind.Content)
    is StickerSource.Starter -> "file:///android_asset/${source.assetPath}"
}

@Composable
private fun StickerPickerDialogs(state: StickerPickerState) {
    when (val making = state.making) {
        is MakingState.Ready -> MadeStickerDialog(made = making, isSaving = false, state = state)
        is MakingState.Saving -> MadeStickerDialog(made = MakingState.Ready(making.made), isSaving = true, state = state)
        MakingState.Idle, MakingState.Working -> Unit
    }
    state.removing?.let {
        ConfirmationDialog(
            title = stringResource(R.string.screen_stickers_remove_title),
            content = stringResource(R.string.screen_stickers_remove_message),
            submitText = stringResource(R.string.screen_stickers_remove),
            destructiveSubmit = true,
            onSubmitClick = { state.eventSink(StickerPickerEvent.ConfirmRemove) },
            onDismiss = { state.eventSink(StickerPickerEvent.CancelRemove) },
        )
    }
    state.problem?.let { problem ->
        ErrorDialog(
            content = stringResource(
                when (problem) {
                    StickerProblem.SEND_FAILED -> R.string.screen_stickers_send_failed
                    StickerProblem.MAKE_FAILED -> R.string.screen_stickers_make_failed
                    StickerProblem.SAVE_FAILED -> R.string.screen_stickers_save_failed
                    StickerProblem.REMOVE_FAILED -> R.string.screen_stickers_remove_failed
                }
            ),
            onSubmit = { state.eventSink(StickerPickerEvent.DismissProblem) },
        )
    }
}

/** The sticker just made, on a sheet of its own: save it, save and send it, or drop it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MadeStickerDialog(made: MakingState.Ready, isSaving: Boolean, state: StickerPickerState) {
    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) state.eventSink(StickerPickerEvent.CancelMade) },
        scrollable = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.screen_stickers_preview_title), style = ElementTheme.typography.fontHeadingSmMedium)
            AsyncImage(
                model = made.made.picture.bytes,
                contentDescription = stringResource(R.string.screen_stickers_preview_title),
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ElementTheme.colors.bgSubtleSecondary),
            )
            if (!made.made.isCutOut) {
                Text(
                    text = stringResource(R.string.screen_stickers_not_cut_out),
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            Button(
                text = stringResource(R.string.screen_stickers_save_and_send),
                showProgress = isSaving,
                enabled = !isSaving,
                onClick = { state.eventSink(StickerPickerEvent.SaveMade(andSend = true)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                text = stringResource(R.string.screen_stickers_save),
                enabled = !isSaving,
                onClick = { state.eventSink(StickerPickerEvent.SaveMade(andSend = false)) },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                text = stringResource(io.element.android.libraries.ui.strings.CommonStrings.action_cancel),
                enabled = !isSaving,
                onClick = { state.eventSink(StickerPickerEvent.CancelMade) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val DIMMED_ALPHA = 0.4f

@PreviewsDayNight
@Composable
internal fun StickerPickerContentPreview(@PreviewParameter(StickerPickerStatePreviewParam::class) state: StickerPickerState) = ElementPreview {
    StickerPickerContent(state)
}
