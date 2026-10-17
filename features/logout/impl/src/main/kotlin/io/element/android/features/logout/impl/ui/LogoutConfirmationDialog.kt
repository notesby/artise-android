/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.logout.impl.ui

import androidx.compose.runtime.Composable
import co.artise.android.workspace.api.signOutConfirmationTexts
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog

@Composable
fun LogoutConfirmationDialog(
    onSubmitClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Artise: names the workspace, and says when Artise ID is signed out too.
    val texts = signOutConfirmationTexts()
    ConfirmationDialog(
        title = texts.title,
        content = texts.content,
        submitText = texts.submit,
        onSubmitClick = onSubmitClick,
        onDismiss = onDismiss,
    )
}
