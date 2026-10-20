/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.note

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import co.artise.android.notes.api.Backlink
import co.artise.android.notes.impl.ui.common.NoteNameProblem
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

open class NoteStatePreviewParam : PreviewParameterProvider<NoteState> {
    override val values: Sequence<NoteState>
        get() = sequenceOf(
            aNoteState(),
            aNoteState(hasLocalEdits = true, backlinks = BacklinksState.Offline),
            aNoteState(content = null, isLoading = true, backlinks = BacklinksState.Loading),
            aNoteState(content = null),
            aNoteState(dialog = NoteDialog.MissingNote("Leche")),
            aNoteState(
                dialog = NoteDialog.ChooseNote(
                    "Ana",
                    persistentListOf(
                        LinkCandidate("people/Ana.md", "Ana", "people", "Mamá de Hector, vive en Monterrey"),
                        LinkCandidate("clients/Ana.md", "Ana", "clients", "Dueña de la panadería"),
                    ),
                ),
            ),
            aNoteState(
                content = "---\ntype: person\naliases: [Mamá, Ana María]\n---\n# Ana\nVive en Monterrey.",
                page = PageInfo("person", persistentListOf("Mamá", "Ana María")),
            ),
            aNoteState(dialog = NoteDialog.Rename("Súper", NoteNameProblem.EXISTS)),
            aNoteState(dialog = NoteDialog.ConfirmDelete),
            aNoteState(dialog = NoteDialog.RenameFailed(RenameFailure.OFFLINE)),
        )
}

fun aNoteState(
    content: String? = "# Súper\n- [x] leche\n- [ ] pan\n\nPara el [[Recetas/Mole|mole]].",
    isLoading: Boolean = false,
    hasLocalEdits: Boolean = false,
    page: PageInfo? = null,
    backlinks: BacklinksState = BacklinksState.Loaded(persistentListOf(Backlink("Recetas/Mole.md", "Comprar todo en [[Súper]]."))),
    dialog: NoteDialog? = null,
) = NoteState(
    title = "Súper",
    content = content,
    isLoading = isLoading,
    hasLocalEdits = hasLocalEdits,
    page = page,
    backlinks = backlinks,
    dialog = dialog,
    embeds = persistentMapOf(),
    openFile = null,
    eventSink = {},
)
