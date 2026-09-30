/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appconfig

object RoomListConfig {
    const val SHOW_INVITE_MENU_ITEM = false
    const val SHOW_REPORT_PROBLEM_MENU_ITEM = false

    // Artise: a notes icon in the chats top bar opens every chat's notes (not hidden in the menu).
    const val SHOW_NOTES_BUTTON = true

    const val HAS_DROP_DOWN_MENU = SHOW_INVITE_MENU_ITEM || SHOW_REPORT_PROBLEM_MENU_ITEM
}
