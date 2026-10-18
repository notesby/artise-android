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
    namespace = "co.artise.android.analytics"
}

setupDependencyInjection()

dependencies {
    implementation(platform(libs.google.firebase.bom))
    implementation("com.google.firebase:firebase-analytics")
    implementation(projects.libraries.core)
    implementation(projects.libraries.di)
    implementation(projects.services.analyticsproviders.api)
    implementation(libs.timber)

    testCommonDependencies(libs)
}
