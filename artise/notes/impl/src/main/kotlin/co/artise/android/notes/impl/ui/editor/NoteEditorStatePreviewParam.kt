/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import co.artise.android.notes.impl.dashboard.DashboardText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

open class NoteEditorStatePreviewParam : PreviewParameterProvider<NoteEditorState> {
    override val values: Sequence<NoteEditorState>
        get() = sequenceOf(
            // Live preview: the cursor is on "leche", every other line shows formatted.
            aNoteEditorState(cursor = 16),
            aNoteEditorState(
                text = "Para el [[mo",
                cursor = 12,
                suggestions = listOf(
                    WikiLinkSuggestion(
                        "Mole",
                        "Recetas/Mole.md",
                        "Recetas/Mole|Mole",
                        folder = "Recetas",
                        summary = "La receta de la abuela",
                        sharesName = true
                    ),
                    WikiLinkSuggestion("Mole", "Viejo/Mole.md", "Viejo/Mole|Mole", folder = "Viejo", sharesName = true),
                    WikiLinkSuggestion("Ana", "people/Ana.md", "Ana|Mamá", alias = "Mamá", folder = "people", summary = "Mamá de Hector", type = "person"),
                ),
            ),
            aNoteEditorState(text = "- leche" + NoteEditorPresenter.CONFLICT_SEPARATOR + "- pan", isResolvingConflict = true),
            aNoteEditorState(showSaveChangesDialog = true),
            aNoteEditorState(find = EditorFind(query = "leche", matches = persistentListOf(2..6), current = 0)),
            aNoteEditorState(isPreviewing = true),
            aNoteEditorState(text = aDashboardText(), dashboard = aDashboardEditing()),
        )
}

fun aNoteEditorState(
    text: String = "# Súper\n- [ ] leche\n- [x] **pan** integral\nVer [[Recetas/Mole|el mole]]",
    cursor: Int = text.length,
    isResolvingConflict: Boolean = false,
    suggestions: List<WikiLinkSuggestion> = emptyList(),
    showSaveChangesDialog: Boolean = false,
    find: EditorFind? = null,
    isPreviewing: Boolean = false,
    dashboard: DashboardEditing? = null,
) = NoteEditorState(
    title = "Súper",
    value = TextFieldValue(text, TextRange(cursor)),
    rawLines = LivePreview.rawLines(text, cursor, cursor),
    isLoading = false,
    isResolvingConflict = isResolvingConflict,
    hasUnsavedChanges = false,
    suggestions = persistentListOf(*suggestions.toTypedArray()),
    notePaths = persistentListOf("Recetas/Mole.md", "Súper.md"),
    pages = persistentListOf(),
    linkEdit = null,
    isAttaching = false,
    isPreviewing = isPreviewing,
    embeds = persistentMapOf(),
    openFile = null,
    openFileProblem = false,
    attachError = null,
    canUndo = true,
    canRedo = false,
    showSaveChangesDialog = showSaveChangesDialog,
    find = find,
    dashboard = dashboard,
    eventSink = {},
)

internal fun aDashboardEditing(showLayout: Boolean = true): DashboardEditing {
    val text = aDashboardText()
    val paths = listOf("charts/ventas.chart", "charts/cobros.chart")
    return DashboardEditing(
        blocks = DashboardText.blocks(text, paths),
        problems = DashboardText.problems(text, paths),
        previews = persistentMapOf(),
        charts = persistentListOf(ChartChoice("charts/ventas.chart", "Ventas por mes"), ChartChoice("charts/cobros.chart", "Cobros")),
        theme = DashboardText.theme(text),
        showLayout = showLayout,
    )
}

internal fun aDashboardText() = "---\ntype: dashboard\ntitle: Octubre\ntheme: {accent: \"#22408A\", mode: auto}\n---\n" +
    "## Ventas\n![[charts/ventas.chart]]\n::: columns\n![[charts/cobros.chart]]\n![[clientes.chart]]\n:::\nNotas del mes."
