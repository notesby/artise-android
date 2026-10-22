/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.reactions.api

import android.content.res.Configuration
import android.os.Build
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class AskedReactionsTest {
    private val recents = listOf("👍️", "👎️", "🔥", "❤️", "👏")

    @Test
    fun `a proposal carries both keys`() {
        val asked = AskedReactions.read(
            """{"type":"m.room.message","content":{"body":"React ✅ to send it","co.artise.reactions":["✅","❌"],"co.artise.proposal":"inbox_reply"}}"""
        )
        assertThat(asked).isEqualTo(AskedReactions(emojis = listOf("✅", "❌"), isProposal = true))
    }

    @Test
    fun `reactions alone are not a proposal`() {
        val asked = AskedReactions.read("""{"content":{"body":"Remind you later?","co.artise.reactions":["⏰","🔁"]}}""")
        assertThat(asked).isEqualTo(AskedReactions(emojis = listOf("⏰", "🔁"), isProposal = false))
    }

    @Test
    fun `the keys are also read from a bare content`() {
        val asked = AskedReactions.read("""{"msgtype":"m.text","body":"Done?","co.artise.reactions":["✅"]}""")
        assertThat(asked?.emojis).containsExactly("✅")
    }

    @Test
    fun `a message without the keys asks for nothing`() {
        assertThat(AskedReactions.read("""{"content":{"msgtype":"m.text","body":"Hola"}}""")).isNull()
        assertThat(AskedReactions.read(null)).isNull()
        assertThat(AskedReactions.read("")).isNull()
        assertThat(AskedReactions.read("not json")).isNull()
    }

    @Test
    fun `a malformed list is ignored`() {
        assertThat(AskedReactions.read("""{"content":{"co.artise.reactions":"✅"}}""")).isNull()
        assertThat(AskedReactions.read("""{"content":{"co.artise.reactions":["✅", 3]}}""")).isNull()
        assertThat(AskedReactions.read("""{"content":{"co.artise.reactions":[{"k":"✅"}]}}""")).isNull()
        // The proposal still counts when its list is broken
        val proposal = AskedReactions.read("""{"content":{"co.artise.reactions":42,"co.artise.proposal":"invoice"}}""")
        assertThat(proposal).isEqualTo(AskedReactions(emojis = emptyList(), isProposal = true))
    }

    @Test
    fun `a variation selector is kept as sent`() {
        val asked = AskedReactions.read("""{"content":{"co.artise.reactions":["✔️","❌"]}}""")
        assertThat(asked?.emojis).containsExactly("✔️", "❌").inOrder()
    }

    @Test
    fun `the quick bar puts the asked emojis first without duplicates`() {
        val bar = AskedReactions.quickBar(listOf("✅", "❌"), listOf("👍️", "✅️", "🔥", "❌", "🔥"))
        assertThat(bar).containsExactly("✅", "❌", "👍️", "🔥").inOrder()
    }

    @Test
    fun `the quick bar is unchanged when nothing is asked`() {
        assertThat(AskedReactions.quickBar(emptyList(), recents)).isEqualTo(recents)
    }

    @Test
    fun `approve and decline label a proposal only`() {
        assertThat(AskedReactions.proposalLabel("✅", isProposal = true)).isEqualTo(R.string.artise_reaction_approve)
        assertThat(AskedReactions.proposalLabel("✅️", isProposal = true)).isEqualTo(R.string.artise_reaction_approve)
        assertThat(AskedReactions.proposalLabel("❌", isProposal = true)).isEqualTo(R.string.artise_reaction_decline)
        assertThat(AskedReactions.proposalLabel("👍", isProposal = true)).isNull()
        assertThat(AskedReactions.proposalLabel("✅", isProposal = false)).isNull()
        assertThat(AskedReactions.proposalLabel("❌", isProposal = false)).isNull()
    }

    @Test
    fun `the labels exist in English Spanish and Portuguese`() {
        assertThat(labels("en")).containsExactly("Approve", "Decline").inOrder()
        assertThat(labels("es")).containsExactly("Aprobar", "Rechazar").inOrder()
        assertThat(labels("pt")).containsExactly("Aprovar", "Recusar").inOrder()
    }

    private fun labels(language: String): List<String> {
        val base = RuntimeEnvironment.getApplication()
        val configuration = Configuration(base.resources.configuration).apply { setLocale(Locale(language)) }
        val context = base.createConfigurationContext(configuration)
        return listOf(
            context.getString(R.string.artise_reaction_approve),
            context.getString(R.string.artise_reaction_decline),
        )
    }
}
