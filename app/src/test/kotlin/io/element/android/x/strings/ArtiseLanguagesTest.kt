/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.x.strings

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Artise: every Artise text (the artise/ modules, and the artise_*.xml files elsewhere) has Spanish and Portuguese, so a phone in either
 * language never falls back to English. Texts marked translatable="false" are exempt.
 */
class ArtiseLanguagesTest {
    private val repoRoot = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val languages = listOf("values-es", "values-pt")

    /** Texts that read the same in every language: names, and the placeholders around them. */
    private val sameEverywhere = setOf("Artise", "Artise ID", "Ari", "%1\$s", "%1\$s · %2\$s")

    /** Words that one language takes from English unchanged. */
    private val borrowed = mapOf(
        "values-es" to setOf("common_room", "screen_stickers_open"),
    )

    private fun skipped(dir: File) = dir.name == "build" || dir.name == ".git" || dir.name == "node_modules" || dir.name == ".gradle"

    /** Each English Artise file: everything under artise/, plus artise_*.xml anywhere else. */
    private fun englishFiles(): List<File> = repoRoot.walkTopDown()
        .onEnter { !skipped(it) }
        .filter { it.isFile && it.extension == "xml" && it.parentFile.name == "values" && it.parentFile.parentFile.name == "res" }
        .filter { it.relativeTo(repoRoot).path.startsWith("artise${File.separator}") || it.name.startsWith("artise_") }
        .toList()

    /** Key to text (plurals joined by "|") in one file, without the translatable="false" ones. */
    private fun texts(file: File): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val children = root.childNodes
        return (0 until children.length).map { children.item(it) }.filterIsInstance<Element>()
            .filter { (it.tagName == "string" || it.tagName == "plurals") && it.getAttribute("translatable") != "false" }
            .associate { element ->
                val text = if (element.tagName == "plurals") {
                    val items = element.getElementsByTagName("item")
                    (0 until items.length).joinToString("|") { items.item(it).textContent }
                } else {
                    element.textContent
                }
                element.getAttribute("name") to text.trim().removeSurrounding("\"")
            }
    }

    /** Every text in a res folder for one language, from all of its files. */
    private fun folderTexts(englishFile: File, language: String): Map<String, String> =
        File(englishFile.parentFile.parentFile, language).listFiles { file -> file.extension == "xml" }.orEmpty()
            .fold(emptyMap()) { all, file -> all + texts(file) }

    /** Keys that Element translates for Brazil: on a pt-BR phone those win over our values-pt, so they need our own pt-rBR too. */
    private fun elementBrazilKeys(): Set<String> = repoRoot.walkTopDown()
        .onEnter { !skipped(it) }
        .filter { it.isFile && it.name == "translations.xml" && it.parentFile.name == "values-pt-rBR" }
        .flatMap { texts(it).keys }
        .toSet()

    @Test
    fun `every Artise text is in Spanish and Portuguese`() {
        val files = englishFiles()
        assertWithMessage("Artise string files").that(files).isNotEmpty()
        val missing = files.flatMap { file ->
            val english = texts(file).keys
            languages.flatMap { language ->
                (english - folderTexts(file, language).keys).map { "${file.relativeTo(repoRoot)} $language $it" }
            }
        }
        assertWithMessage("missing translations").that(missing).isEmpty()
    }

    @Test
    fun `no translation is the English text copied`() {
        val copied = englishFiles().flatMap { file ->
            val english = texts(file)
            languages.flatMap { language ->
                val translated = folderTexts(file, language)
                english.filter { (key, text) -> text !in sameEverywhere && key !in borrowed[language].orEmpty() && translated[key] == text }
                    .map { (key, _) -> "${file.relativeTo(repoRoot)} $language $key" }
            }
        }
        assertWithMessage("English copied into a translation").that(copied).isEmpty()
    }

    @Test
    fun `texts Element translates for Brazil have an Artise pt-rBR text`() {
        val brazil = elementBrazilKeys()
        val missing = englishFiles().flatMap { file ->
            val brazilian = folderTexts(file, "values-pt-rBR")
            (texts(file).keys intersect brazil).filter { it !in brazilian }.map { "${file.relativeTo(repoRoot)} $it" }
        }
        assertWithMessage("missing values-pt-rBR").that(missing).isEmpty()
    }
}
