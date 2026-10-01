/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

import extension.setupDependencyInjection
import extension.testCommonDependencies

plugins {
    id("io.element.android-library")
}

android {
    namespace = "co.artise.android.linkpreview.impl"
}

setupDependencyInjection()

dependencies {
    api(projects.artise.linkpreview.api)
    implementation(projects.libraries.core)
    implementation(projects.libraries.di)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.network)
    implementation(libs.network.okhttp.okhttp)
    implementation(platform(libs.network.okhttp.bom))
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    implementation(libs.jsoup)
    implementation(libs.timber)

    testCommonDependencies(libs)
    testImplementation(libs.network.mockwebserver)
}
