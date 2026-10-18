/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

// Artise: notification sounds picked from the person's own files, per chat. Local to the phone, nothing is synced.
plugins {
    id("io.element.android-compose-library")
}

android {
    namespace = "co.artise.android.sounds.api"
}

dependencies {
    api(projects.libraries.architecture)
    api(projects.libraries.matrix.api)
    implementation(projects.libraries.designsystem)
    implementation(projects.libraries.uiStrings)
    api(libs.kotlinx.collections.immutable)
    api(libs.coroutines.core)
}
