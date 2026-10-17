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
    namespace = "co.artise.android.recovery.impl"
}

setupDependencyInjection()

dependencies {
    api(projects.artise.recovery.api)
    implementation(projects.libraries.core)
    implementation(projects.libraries.di)
    implementation(projects.libraries.matrix.api)
    implementation(libs.coroutines.core)
    implementation(libs.timber)

    testCommonDependencies(libs)
    testImplementation(projects.artise.recovery.test)
    testImplementation(projects.libraries.matrix.test)
}
