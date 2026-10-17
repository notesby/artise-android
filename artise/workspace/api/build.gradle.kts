/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

// Artise workspaces: one app for every company's server. The lookup, the workspace file and its branding.
plugins {
    id("io.element.android-compose-library")
}

android {
    namespace = "co.artise.android.workspace.api"
}

dependencies {
    api(projects.libraries.matrix.api)
    api(projects.libraries.architecture)
    implementation(libs.coil.compose)
    implementation(projects.libraries.designsystem)
    implementation(projects.libraries.uiStrings)
}
