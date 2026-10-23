/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import co.artise.android.notes.api.ExportFormat
import co.artise.android.notes.api.NotesExport
import co.artise.android.notes.impl.R
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.dialogs.AlertDialog
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.DropdownMenu
import io.element.android.libraries.designsystem.theme.components.DropdownMenuItem
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import kotlinx.collections.immutable.ImmutableList
import timber.log.Timber

/**
 * The top bar's Share: a menu with the [formats] the server can make of the document and "Ask Ari for a link"; a spinner
 * while the file is made. The file goes to the system share sheet (WhatsApp, email, files) once it's ready.
 */
@Composable
internal fun ShareAction(state: DocumentShareState, formats: ImmutableList<ExportFormat>, title: String) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    state.ready?.let { export ->
        LaunchedEffect(export) {
            context.shareExport(export)
            state.eventSink(DocumentShareEvent.ExportHandled)
        }
    }
    Box {
        if (state.exporting != null) {
            val preparing = stringResource(R.string.a11y_notes_share_preparing)
            Box(modifier = Modifier.size(48.dp).semantics { contentDescription = preparing }, contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        } else {
            IconButton(onClick = { showMenu = true }) {
                Icon(imageVector = CompoundIcons.ShareAndroid(), contentDescription = stringResource(R.string.screen_notes_share))
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            formats.forEach { format ->
                DropdownMenuItem(
                    text = { Text(stringResource(if (format == ExportFormat.PNG) R.string.screen_notes_share_png else R.string.screen_notes_share_pdf)) },
                    onClick = {
                        showMenu = false
                        state.eventSink(DocumentShareEvent.Export(format, title))
                    },
                )
            }
            if (state.chat?.canAsk == true) {
                val request = stringResource(R.string.screen_notes_share_ask_link_message, title)
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(R.string.screen_notes_share_ask_link))
                            if (state.chat.othersSee) {
                                Text(
                                    text = stringResource(R.string.screen_notes_share_ask_link_note),
                                    style = ElementTheme.typography.fontBodySmRegular,
                                    color = ElementTheme.colors.textSecondary,
                                )
                            }
                        }
                    },
                    onClick = {
                        showMenu = false
                        state.eventSink(DocumentShareEvent.AskForLink(request))
                    },
                )
            }
        }
    }
    ShareDialogs(state)
}

@Composable
private fun ShareDialogs(state: DocumentShareState) {
    val dismiss = { state.eventSink(DocumentShareEvent.DismissDialog) }
    when (state.problem) {
        ShareProblem.EXPORT_FAILED -> ErrorDialog(content = stringResource(R.string.screen_notes_share_export_failed), onSubmit = dismiss)
        ShareProblem.LINK_FAILED -> ErrorDialog(content = stringResource(R.string.screen_notes_share_ask_link_failed), onSubmit = dismiss)
        null -> if (state.linkAsked) AlertDialog(content = stringResource(R.string.screen_notes_share_ask_link_sent), onDismiss = dismiss)
    }
}

/** The system share sheet with the file, through the app's FileProvider (the cache is what it shares). */
private fun Context.shareExport(export: NotesExport) {
    val uri = try {
        FileProvider.getUriForFile(this, "$packageName.fileprovider", export.file)
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "Export outside the shared cache")
        return
    }
    val send = Intent(Intent.ACTION_SEND)
        .setType(export.mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TITLE, export.name)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        startActivity(Intent.createChooser(send, export.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "No app to share with")
    }
}
