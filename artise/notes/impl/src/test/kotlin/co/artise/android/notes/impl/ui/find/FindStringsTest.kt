/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.find

import android.content.res.Configuration
import android.os.Build
import co.artise.android.notes.impl.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class FindStringsTest {
    /** The counter reads "3 of 12" in English, "3 de 12" in Spanish and Portuguese; "No matches" when there are none. */
    @Test
    fun `the counter is localised`() {
        assertThat(counter("en", 3, 12)).isEqualTo("3 of 12")
        assertThat(counter("es", 3, 12)).isEqualTo("3 de 12")
        assertThat(counter("pt", 3, 12)).isEqualTo("3 de 12")
        assertThat(none("en")).isEqualTo("No matches")
        assertThat(none("es")).isEqualTo("Sin coincidencias")
        assertThat(none("pt")).isEqualTo("Sem ocorrências")
    }

    private fun counter(language: String, current: Int, total: Int) = context(language).getString(R.string.screen_notes_find_count, current, total)

    private fun none(language: String) = context(language).getString(R.string.screen_notes_find_none)

    private fun context(language: String): android.content.Context {
        val base = RuntimeEnvironment.getApplication()
        val configuration = Configuration(base.resources.configuration).apply { setLocale(Locale(language)) }
        return base.createConfigurationContext(configuration)
    }
}
