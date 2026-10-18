/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

// Artise: quiet hours per workspace: its notifications arrive silently at night (and on weekends, if chosen).
plugins {
    id("io.element.android-compose-library")
}

android {
    namespace = "co.artise.android.quiet.api"
}

dependencies {
    api(projects.libraries.architecture)
    api(projects.libraries.matrix.api)
    implementation(projects.libraries.designsystem)
    api(libs.kotlinx.collections.immutable)
}
