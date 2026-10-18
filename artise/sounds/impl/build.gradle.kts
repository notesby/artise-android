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
    namespace = "co.artise.android.sounds.impl"
}

setupDependencyInjection()

dependencies {
    api(projects.artise.sounds.api)
    implementation(projects.features.enterprise.api)
    implementation(projects.libraries.architecture)
    implementation(projects.libraries.core)
    implementation(projects.libraries.di)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.sessionStorage.api)
    implementation(projects.services.toolbox.api)
    implementation(libs.serialization.json)
    implementation(libs.timber)

    testCommonDependencies(libs)
    testImplementation(projects.libraries.matrix.test)
    testImplementation(projects.artise.sounds.test)
    testImplementation(projects.services.toolbox.test)
}
