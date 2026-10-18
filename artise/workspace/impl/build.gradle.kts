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
    id("kotlin-parcelize")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "co.artise.android.workspace.impl"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

setupDependencyInjection()

dependencies {
    api(projects.artise.workspace.api)
    implementation(projects.libraries.architecture)
    implementation(projects.libraries.core)
    implementation(projects.libraries.designsystem)
    implementation(projects.libraries.androidutils)
    implementation(projects.libraries.uiStrings)
    implementation(projects.libraries.di)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.network)
    implementation(projects.libraries.cryptography.api)
    implementation(projects.artise.appicon)
    implementation(projects.libraries.sessionStorage.api)
    implementation(projects.features.enterprise.api)
    implementation(projects.services.appnavstate.api)
    implementation(projects.services.toolbox.api)
    implementation(libs.network.okhttp.okhttp)
    implementation(platform(libs.network.okhttp.bom))
    implementation(libs.serialization.json)
    implementation(libs.timber)

    testCommonDependencies(libs)
    testImplementation(projects.libraries.matrix.test)
    testImplementation(projects.services.toolbox.test)
    testImplementation(projects.services.appnavstate.test)
    testImplementation(libs.network.mockwebserver)
    testImplementation(projects.artise.workspace.test)
    testImplementation(projects.libraries.cryptography.test)
    testImplementation(projects.libraries.cryptography.impl)
    testImplementation(projects.libraries.sessionStorage.test)
}
