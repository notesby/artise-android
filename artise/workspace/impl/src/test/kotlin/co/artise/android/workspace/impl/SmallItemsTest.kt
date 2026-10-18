/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl

import co.artise.android.workspace.api.WorkspaceConfig
import co.artise.android.workspace.api.withAssistantFirst
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmallItemsTest {
    private data class Chat(val name: String, val dmWith: String?)

    private val chats = listOf(Chat("Mamá", "@mama:x"), Chat("Tienda", null), Chat("Ari", "@wikibot:x"), Chat("Club", null))

    /** Ari's chat moves to the top; the others keep their order. Without an assistant, or without its chat, nothing moves. */
    @Test
    fun `ari's chat comes first`() {
        val isAri = { chat: Chat, id: String -> chat.dmWith == id }
        assertThat(chats.withAssistantFirst("@wikibot:x", isAri).map { it.name }).containsExactly("Ari", "Mamá", "Tienda", "Club").inOrder()
        assertThat(chats.withAssistantFirst(null, isAri)).isEqualTo(chats)
        assertThat(chats.withAssistantFirst("@nobody:x", isAri)).isEqualTo(chats)
    }

    /** "Actualiza Artise" only when the workspace names a minimum and this app is older. */
    @Test
    fun `the update notice needs a minimum`() {
        assertThat(WorkspaceConfig().needsAppUpdate(202_609_100)).isFalse()
        assertThat(WorkspaceConfig(minAndroidVersionCode = 202_609_100).needsAppUpdate(202_609_100)).isFalse()
        assertThat(WorkspaceConfig(minAndroidVersionCode = 202_610_010).needsAppUpdate(202_609_100)).isTrue()
    }
}
