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
}

android {
    namespace = "co.artise.android.enterprise"
}

setupDependencyInjection()

dependencies {
    implementation(projects.libraries.compound)
    api(projects.features.enterprise.api)
    implementation(projects.features.enterprise.implFoss)
    implementation(projects.libraries.architecture)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.di)

    testCommonDependencies(libs)
    testImplementation(projects.libraries.matrix.test)
}
