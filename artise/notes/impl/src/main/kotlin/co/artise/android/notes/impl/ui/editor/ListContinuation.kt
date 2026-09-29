/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.text.TextRange

/**
 * Makes Enter behave like in a notes app: on a list line it starts the next item, and on an empty item it ends
 * the list (see [MarkdownFormatting.continueListOnEnter]). Anything other than a single typed newline is left alone.
 */
internal object ListContinuation : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        // A single typed newline: one character longer, cursor just after a "\n", everything else unchanged.
        val before = originalText.toString()
        val cursor = selection.start
        if (!selection.collapsed || length != before.length + 1 || cursor == 0) return
        val now = asCharSequence()
        if (now[cursor - 1] != '\n' || now.substring(0, cursor - 1) + now.substring(cursor) != before) return
        val result = MarkdownFormatting.continueListOnEnter(before, cursor - 1) ?: return
        replace(0, length, result.text)
        selection = TextRange(result.start, result.end)
    }
}
