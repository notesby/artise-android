/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.x.strings

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * Artise: artise_wording_strings.xml (app/src/main/res, one per language) rewords Element's texts (chat, sign out, workspace). These tests fail when
 * an upstream merge renames or drops one of those keys, or changes its placeholders.
 */
class ArtiseWordingStringsTest {
    private val repoRoot = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val stringPattern = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val placeholderPattern = Regex("""%\d+\$[sd]""")

    private fun strings(file: File): Map<String, String> =
        stringPattern.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2] }

    private fun overrides(folder: String) = strings(File(repoRoot, "app/src/main/res/$folder/artise_wording_strings.xml"))

    /** Element's English texts, from every module's generated Localazy file. */
    private fun upstreamEnglish(): Map<String, String> = repoRoot.walkTopDown()
        .onEnter { it.name != "build" && it.name != ".git" && it.name != "node_modules" }
        .filter { it.name == "localazy.xml" && it.parentFile.name == "values" }
        .fold(emptyMap()) { all, file -> all + strings(file) }

    private fun placeholders(text: String) = placeholderPattern.findAll(text).map { it.value }.toSortedSet()

    /** Every reworded key still exists in Element's texts, with the same placeholders, so the override still takes effect. */
    @Test
    fun `every override still matches an Element text`() {
        val upstream = upstreamEnglish()
        val english = overrides("values")
        assertThat(english).isNotEmpty()
        assertThat(english.keys - upstream.keys).isEmpty()
        english.forEach { (key, text) ->
            assertWithMessage(key).that(placeholders(text)).isEqualTo(placeholders(upstream.getValue(key)))
        }
    }

    /** Spanish and both Portuguese folders reword exactly the same keys as English, with the same placeholders. */
    @Test
    fun `every language rewords the same keys`() {
        val english = overrides("values")
        listOf("values-es", "values-pt", "values-pt-rBR").forEach { folder ->
            val translated = overrides(folder)
            assertWithMessage(folder).that(translated.keys).isEqualTo(english.keys)
            translated.forEach { (key, text) ->
                assertWithMessage("$folder $key").that(placeholders(text)).isEqualTo(placeholders(english.getValue(key)))
            }
        }
    }
}
