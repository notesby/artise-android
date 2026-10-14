/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.signin

import android.content.Context
import androidx.core.content.edit
import co.artise.android.workspace.api.SignInWorkspace
import co.artise.android.workspace.api.SignInWorkspaceHolder
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultSignInWorkspaceHolder(
    @ApplicationContext context: Context,
) : SignInWorkspaceHolder {
    private val preferences by lazy { context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }
    private val chosen = MutableStateFlow<SignInWorkspace?>(null)

    override val current: StateFlow<SignInWorkspace?> = chosen.asStateFlow()

    override fun choose(workspace: SignInWorkspace) {
        chosen.value = workspace
        preferences.edit { putString(LAST_TYPED, workspace.typed) }
    }

    override fun lastTyped(): String? = preferences.getString(LAST_TYPED, null)

    private companion object {
        const val PREFERENCES = "artise_workspace_signin"
        const val LAST_TYPED = "last_typed"
    }
}
