/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.sounds.api

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import kotlinx.collections.immutable.persistentListOf

/**
 * The "Sonido" row in a chat's notification settings: the chat's current sound, and on tap a sheet to pick Predeterminado, one of
 * "Tus sonidos" (with a preview), a new file, one of the phone's sounds, or no sound. Hidden below Android 10.
 */
@Composable
fun ChatSoundRow(state: ChatSoundState, modifier: Modifier = Modifier) {
    if (!state.isSupported) return
    ListItem(
        modifier = modifier,
        supportingContent = { Text(choiceLabel(state)) },
        onClick = { state.eventSink(ChatSoundEvent.OpenPicker) },
    ) {
        Text(stringResource(R.string.artise_sound_title))
    }
    if (state.isPickerVisible) {
        ChatSoundPicker(state)
    }
    state.importProblem?.let { problem ->
        ErrorDialog(content = problemText(problem), onSubmit = { state.eventSink(ChatSoundEvent.ClearProblem) })
    }
}

@Composable
private fun choiceLabel(state: ChatSoundState): String = when {
    state.changedInPhoneSettings -> stringResource(R.string.artise_sound_changed_in_phone_settings)
    else -> when (val choice = state.choice) {
        ChatSoundChoice.Default -> stringResource(R.string.artise_sound_default)
        ChatSoundChoice.Silent -> stringResource(R.string.artise_sound_silent)
        is ChatSoundChoice.Phone -> stringResource(R.string.artise_sound_phone_chosen)
        is ChatSoundChoice.Saved -> state.sounds.firstOrNull { it.id == choice.soundId }?.name ?: stringResource(R.string.artise_sound_default)
    }
}

@Composable
private fun problemText(problem: SoundImportResult): String = when (problem) {
    is SoundImportResult.TooLong -> stringResource(R.string.artise_sound_problem_too_long, problem.seconds)
    SoundImportResult.TooBig -> stringResource(R.string.artise_sound_problem_too_big)
    SoundImportResult.NotAudio -> stringResource(R.string.artise_sound_problem_not_audio)
    SoundImportResult.Failed,
    is SoundImportResult.Imported -> stringResource(R.string.artise_sound_problem_failed)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSoundPicker(state: ChatSoundState) {
    val context = LocalContext.current
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { state.eventSink(ChatSoundEvent.Import(it.toString())) }
    }
    val pickPhoneSound = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked = result.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            state.eventSink(ChatSoundEvent.Choose(picked?.let { ChatSoundChoice.Phone(it.toString()) } ?: ChatSoundChoice.Silent))
        }
    }
    ModalBottomSheet(
        onDismissRequest = { state.eventSink(ChatSoundEvent.ClosePicker) },
        scrollable = true,
    ) {
        PickerRow(
            title = stringResource(R.string.artise_sound_default),
            description = stringResource(R.string.artise_sound_default_description),
            selected = state.choice == ChatSoundChoice.Default,
        ) { state.eventSink(ChatSoundEvent.Choose(ChatSoundChoice.Default)) }
        state.sounds.forEach { sound ->
            SavedSoundRow(context, sound, selected = state.choice == ChatSoundChoice.Saved(sound.id), state = state)
        }
        ListItem(
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Plus())),
            onClick = { pickFile.launch(arrayOf("audio/*")) },
        ) {
            Text(stringResource(R.string.artise_sound_pick_file))
        }
        PickerRow(
            title = stringResource(R.string.artise_sound_phone),
            description = null,
            selected = state.choice is ChatSoundChoice.Phone,
        ) { pickPhoneSound.launch(phoneSoundPicker(state.choice)) }
        PickerRow(
            title = stringResource(R.string.artise_sound_silent),
            description = null,
            selected = state.choice == ChatSoundChoice.Silent,
        ) { state.eventSink(ChatSoundEvent.Choose(ChatSoundChoice.Silent)) }
    }
}

@Composable
private fun ColumnScope.PickerRow(title: String, description: String?, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        supportingContent = description?.let { { Text(it) } },
        leadingContent = ListItemContent.RadioButton(selected = selected),
        onClick = onClick,
    ) {
        Text(title)
    }
}

@Composable
private fun ColumnScope.SavedSoundRow(context: Context, sound: SavedSound, selected: Boolean, state: ChatSoundState) {
    val ringtone = remember(sound.uri) { RingtoneManager.getRingtone(context, sound.uri.toUri()) }
    ListItem(
        supportingContent = { Text(stringResource(R.string.artise_sound_yours)) },
        leadingContent = ListItemContent.RadioButton(selected = selected),
        trailingContent = ListItemContent.Custom {
            Row {
                IconButton(onClick = { ringtone?.play() }) {
                    Icon(imageVector = CompoundIcons.PlaySolid(), contentDescription = sound.name)
                }
                IconButton(onClick = { state.eventSink(ChatSoundEvent.Remove(sound.id)) }) {
                    Icon(imageVector = CompoundIcons.Delete(), contentDescription = stringResource(R.string.artise_sound_remove))
                }
            }
        },
        onClick = { state.eventSink(ChatSoundEvent.Choose(ChatSoundChoice.Saved(sound.id))) },
    ) {
        Text(sound.name)
    }
}

private fun phoneSoundPicker(current: ChatSoundChoice) = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, (current as? ChatSoundChoice.Phone)?.uri?.toUri())
}

@PreviewsDayNight
@Composable
internal fun ChatSoundRowPreview() = ElementPreview {
    ChatSoundRow(
        ChatSoundState(
            isSupported = true,
            choice = ChatSoundChoice.Saved("1"),
            sounds = persistentListOf(SavedSound("1", "risa-de-sofia.m4a", "content://media/1")),
            changedInPhoneSettings = false,
            isPickerVisible = false,
            importProblem = null,
            eventSink = {},
        )
    )
}
