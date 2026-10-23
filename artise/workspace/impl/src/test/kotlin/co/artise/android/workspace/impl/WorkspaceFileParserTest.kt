/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl

import co.artise.android.workspace.api.WorkspaceBranding
import co.artise.android.workspace.api.WorkspaceConfig
import co.artise.android.workspace.api.WorkspaceLinks
import co.artise.android.workspace.api.WorkspaceOAuth
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkspaceFileParserTest {
    /** Every field of the section is read, next to the standard Matrix keys. */
    @Test
    fun `full section`() {
        val config = WorkspaceFileParser.parse(
            """
            {
              "m.homeserver": {"base_url": "https://matrix.artise.co"},
              "org.matrix.msc4143.rtc_foci": [{"type": "livekit"}],
              "co.artise.workspace": {
                "version": 1,
                "name": "Acme",
                "logo": "https://artise.co/brand/logo-abc.png",
                "colors": {"accent": "#1F6FEB"},
                "assistant_name": "Ari",
                "welcome": {"en": "Welcome to Acme", "es": "Bienvenido"},
                "language": "es",
                "features": ["booking", "tasks"],
                "push_gateway": "https://push.artise.co/_matrix/push/v1/notify"
              }
            }
            """.trimIndent()
        )
        assertThat(config).isEqualTo(
            WorkspaceConfig(
                name = "Acme",
                logoUrl = "https://artise.co/brand/logo-abc.png",
                accentColor = 0xFF1F6FEB,
                assistantName = "Ari",
                welcome = mapOf("en" to "Welcome to Acme", "es" to "Bienvenido"),
                language = "es",
                features = setOf("booking", "tasks"),
                pushGateway = "https://push.artise.co/_matrix/push/v1/notify",
            )
        )
    }

    /** `links` gives full URLs (only https ones are kept); a server without a web host sends none. */
    @Test
    fun `links are read`() {
        val config = WorkspaceFileParser.parse(
            """
            {"co.artise.workspace": {"links": {"web": "https://chat.artise.co", "notes_api": "https://chat.artise.co/api/notes/v1",
            "delete_account": "http://chat.artise.co/account/delete", "status": "https://status.artise.co"}}}
            """.trimIndent()
        )
        assertThat(config?.links).isEqualTo(WorkspaceLinks("https://chat.artise.co", "https://chat.artise.co/api/notes/v1", null))
        assertThat(WorkspaceFileParser.parse("""{"co.artise.workspace": {"name": "Acme"}}""")?.links).isNull()
    }

    /** `oauth` gives the android client and its https redirect; without either (or with an http redirect) there's none. */
    @Test
    fun `oauth is read`() {
        val config = WorkspaceFileParser.parse(
            """
            {"co.artise.workspace": {"oauth": {"ios": "01K7ARTSEAPP1PH0NE00000000", "android": "01K7ARTSEAPPANDR0D00000000",
            "redirect_uri": "https://join.artise.co/oauth"}}}
            """.trimIndent()
        )
        assertThat(config?.oauth).isEqualTo(WorkspaceOAuth("01K7ARTSEAPPANDR0D00000000", "https://join.artise.co/oauth"))
        assertThat(WorkspaceFileParser.parse("""{"co.artise.workspace": {"name": "Acme"}}""")?.oauth).isNull()
        val noAndroidClient = """{"co.artise.workspace": {"oauth": {"ios": "x", "redirect_uri": "https://join.artise.co/oauth"}}}"""
        assertThat(WorkspaceFileParser.parse(noAndroidClient)?.oauth).isNull()
        val httpRedirect = """{"co.artise.workspace": {"oauth": {"android": "x", "redirect_uri": "http://join.artise.co/oauth"}}}"""
        assertThat(WorkspaceFileParser.parse(httpRedirect)?.oauth).isNull()
    }

    /** Only some fields: the rest stay at Artise's defaults. */
    @Test
    fun `partial section`() {
        val config = WorkspaceFileParser.parse("""{"co.artise.workspace": {"name": "Acme"}}""")
        assertThat(config).isEqualTo(WorkspaceConfig(name = "Acme"))
        assertThat(config?.hasFeature("booking")).isTrue()
    }

    /** No section, or a file that isn't JSON: no settings, so the default look (sign-in still works). */
    @Test
    fun `missing section or broken file`() {
        assertThat(WorkspaceFileParser.parse("""{"m.homeserver": {"base_url": "https://matrix.acme.example"}}""")).isNull()
        assertThat(WorkspaceFileParser.parse("<html>not found</html>")).isNull()
        assertThat(WorkspaceFileParser.parse("""{"co.artise.workspace": "oops"}""")).isNull()
    }

    /** A newer version with fields this app doesn't know: the known ones are still read. */
    @Test
    fun `version 2 with unknown fields`() {
        val config = WorkspaceFileParser.parse(
            """{"co.artise.workspace": {"version": 2, "name": "Acme", "hologram": {"x": 1}, "colors": {"accent": "#1F6FEB", "glow": "#FFFFFF"}}}"""
        )
        assertThat(config).isEqualTo(WorkspaceConfig(name = "Acme", accentColor = 0xFF1F6FEB))
    }

    /** Wrong types and bad values are skipped one by one: a colour that isn't #RRGGBB, a logo that isn't https, a number as a name. */
    @Test
    fun `malformed fields are skipped`() {
        val config = WorkspaceFileParser.parse(
            """
            {"co.artise.workspace": {"name": 7, "logo": "http://insecure.example/logo.png", "colors": {"accent": "blue"},
            "welcome": {"en": 3, "es": "Hola"}, "features": "booking"}}
            """.trimIndent()
        )
        assertThat(config).isEqualTo(WorkspaceConfig(welcome = mapOf("es" to "Hola")))
        listOf("#FFF", "#1F6FEB80", "1F6FEB", "#GGGGGG").forEach { assertThat(WorkspaceFileParser.parseColor(it)).isNull() }
    }

    /** mas-k8s's file (17 Oct): Ari's account, the admin to write to, the help page, no minimum version yet, and the Play link. */
    @Test
    fun `help, assistant, contact and update fields are read`() {
        val config = WorkspaceFileParser.parse(
            """
            {"co.artise.workspace": {"links": {"web": "https://chat.mas-k8s.artise.co", "help": "https://artise.co/help"},
            "assistant_user_id": "@wikibot:mas-k8s.artise.co", "contact_user_id": "@hector:mas-k8s.artise.co",
            "min_app_version": {}, "store_links": {"android": "https://play.google.com/store/apps/details?id=co.artise.app"}}}
            """.trimIndent()
        )!!
        assertThat(config.links?.help).isEqualTo("https://artise.co/help")
        assertThat(config.assistantUserId).isEqualTo("@wikibot:mas-k8s.artise.co")
        assertThat(config.contactUserId).isEqualTo("@hector:mas-k8s.artise.co")
        assertThat(config.minAndroidVersionCode).isNull()
        assertThat(config.androidStoreLink).isEqualTo("https://play.google.com/store/apps/details?id=co.artise.app")

        val withMinimum = WorkspaceFileParser.parse("""{"co.artise.workspace": {"min_app_version": {"android": 202609100, "ios": "26.9.10"}}}""")!!
        assertThat(withMinimum.minAndroidVersionCode).isEqualTo(202_609_100L)
        val wrongTypes = WorkspaceFileParser.parse(
            """{"co.artise.workspace": {"min_app_version": {"android": "202609100"}, "assistant_user_id": "wikibot"}}"""
        )!!
        assertThat(wrongTypes.minAndroidVersionCode).isNull()
        assertThat(wrongTypes.assistantUserId).isNull()
    }

    /** An admin's chart palette: valid colours only, at most eight; the branding falls back to the accent alone. */
    @Test
    fun `the chart palette is read`() {
        val config = WorkspaceFileParser.parse(
            """{"co.artise.workspace": {"colors": {"accent": "#1F6FEB", "palette": ["#22408A", "red", "#EB6834", 3,
               "#000001", "#000002", "#000003", "#000004", "#000005", "#000006", "#000007"]}}}""".trimIndent(),
        )
        assertThat(config?.chartPalette).containsExactly(
            0xFF22408A,
            0xFFEB6834,
            0xFF000001,
            0xFF000002,
            0xFF000003,
            0xFF000004,
            0xFF000005,
            0xFF000006,
        ).inOrder()
        assertThat(WorkspaceBranding.of(config).chartColors.take(2)).containsExactly("#22408A", "#EB6834").inOrder()
        val accentOnly = WorkspaceFileParser.parse("""{"co.artise.workspace": {"colors": {"accent": "#1F6FEB"}}}""")
        assertThat(WorkspaceBranding.of(accentOnly).chartColors).containsExactly("#1F6FEB")
        assertThat(WorkspaceBranding.Default.chartColors).isEmpty()
    }
}
