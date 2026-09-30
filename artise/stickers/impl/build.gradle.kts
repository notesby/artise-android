/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

import extension.setupDependencyInjection
import extension.testCommonDependencies

plugins {
    id("io.element.android-compose-library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "co.artise.android.stickers.impl"
}

setupDependencyInjection()

dependencies {
    api(projects.artise.stickers.api)
    implementation(projects.libraries.architecture)
    implementation(projects.libraries.core)
    implementation(projects.libraries.di)
    implementation(projects.libraries.designsystem)
    implementation(projects.libraries.uiStrings)
    implementation(projects.libraries.matrixmedia.api)
    implementation(projects.libraries.sessionStorage.api)
    implementation(projects.libraries.preferences.api)
    implementation(projects.libraries.featureflag.api)
    implementation(projects.features.enterprise.api)
    implementation(libs.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.timber)
    implementation(libs.androidx.exifinterface)
    // Cuts the person or object out of a photo, on the phone (Google Play services downloads the model once).
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")

    testCommonDependencies(libs, true)
    testImplementation(projects.libraries.matrix.test)
}
