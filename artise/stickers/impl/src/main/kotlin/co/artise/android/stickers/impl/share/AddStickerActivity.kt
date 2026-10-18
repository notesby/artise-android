/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.IntentCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import io.element.android.compound.colors.SemanticColorsLightDark
import io.element.android.features.enterprise.api.EnterpriseService
import io.element.android.libraries.architecture.bindings
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.designsystem.theme.ElementThemeApp
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import co.artise.android.stickers.api.R as StickersApiR

/** "Artise sticker" in other apps' share menus: adds the shared picture to the person's stickers. */
class AddStickerActivity : AppCompatActivity() {
    @Inject lateinit var presenterFactory: AddStickerPresenter.Factory
    @Inject lateinit var appPreferencesStore: AppPreferencesStore
    @Inject lateinit var featureFlagService: FeatureFlagService
    @Inject lateinit var enterpriseService: EnterpriseService
    @Inject lateinit var buildMeta: BuildMeta

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        bindings<AddStickerBindings>().inject(this)
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        if (intent.action != Intent.ACTION_SEND || uri == null) {
            finish()
            return
        }
        val presenter = presenterFactory.create(uri.toString())
        setContent {
            val colors by remember { enterpriseService.semanticColorsFlow(sessionId = null) }.collectAsState(SemanticColorsLightDark.default)
            ElementThemeApp(
                appPreferencesStore = appPreferencesStore,
                featureFlagService = featureFlagService,
                compoundLight = colors.light,
                compoundDark = colors.dark,
                buildMeta = buildMeta,
            ) {
                val state = presenter.present()
                LaunchedEffect(state.step) {
                    if (state.step == AddStickerStep.Saved) {
                        Toast.makeText(this@AddStickerActivity, StickersApiR.string.screen_stickers_saved, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                AddStickerView(state = state, onClose = ::finish)
            }
        }
    }
}

@ContributesTo(AppScope::class)
interface AddStickerBindings {
    fun inject(activity: AddStickerActivity)
}
