/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.history

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import co.artise.android.notes.api.ChangeAuthor
import co.artise.android.notes.impl.R

/** Who a change was for and how it was made, ready to be put in words: a string with its arguments, or the server's plain name. */
@Immutable
sealed interface ChangeLabel {
    data class Text(@StringRes val res: Int, val args: List<String> = emptyList()) : ChangeLabel

    data class Plain(val name: String) : ChangeLabel
}

/** Turns the server's `who`/`how`/`detail` into a label: "Ari, asked by Ana", "Ana, in the app", "A synced device"... */
object ChangeLabels {
    fun of(author: ChangeAuthor): ChangeLabel {
        val who = author.who?.trim()?.takeIf { it.isNotEmpty() }
        val detail = author.detail.trim()
        val fallback = ChangeLabel.Plain(author.by)

        // Labels that name a person fall back to the plain name when the server didn't say who.
        fun forWho(@StringRes res: Int, vararg extra: String) = if (who == null) fallback else ChangeLabel.Text(res, listOf(who, *extra))
        return when (author.how) {
            "ari" -> forWho(R.string.screen_notes_change_ari)
            "automation" -> forWho(R.string.screen_notes_change_automation, detail)
            "routine" -> forWho(R.string.screen_notes_change_routine, detail)
            "brief" -> forWho(R.string.screen_notes_change_brief, detail)
            "ari_own" -> ChangeLabel.Text(R.string.screen_notes_change_ari_own)
            "reaction" -> forWho(R.string.screen_notes_change_reaction)
            "chat" -> forWho(R.string.screen_notes_change_chat)
            "app" -> forWho(R.string.screen_notes_change_app)
            "files" -> forWho(R.string.screen_notes_change_files)
            "sync" -> ChangeLabel.Text(R.string.screen_notes_change_sync)
            else -> fallback
        }
    }

    /** Without a [ChangeAuthor.detail] ("#3"), "Ari, Ana's automation " would keep a stray space: tidy it. */
    fun tidy(text: String): String = text.replace(Regex(" {2,}"), " ").trim()
}

@Composable
fun ChangeLabel.text(): String = when (this) {
    is ChangeLabel.Text -> ChangeLabels.tidy(stringResource(res, *args.toTypedArray()))
    is ChangeLabel.Plain -> name
}
