/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

// Artise: the account's chat recovery key, kept in the phone's end-to-end encrypted backup so a new or reinstalled phone opens the chats
// without asking for it.
plugins {
    id("io.element.android-library")
}

android {
    namespace = "co.artise.android.recovery.api"
}

dependencies {
    api(projects.libraries.matrix.api)
}
