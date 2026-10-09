/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

plugins {
    id("io.element.android-compose-library")
}

android {
    namespace = "co.artise.android.linkpreview.api"
}

dependencies {
    api(projects.libraries.matrix.api)
    implementation(projects.libraries.core)
    implementation(projects.libraries.designsystem)
    implementation(projects.libraries.matrixmedia.api)
    implementation(projects.libraries.uiStrings)
    implementation(libs.coil.compose)
}
