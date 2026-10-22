/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

import extension.testCommonDependencies

plugins {
    id("io.element.android-library")
}

android {
    namespace = "co.artise.android.reactions.api"
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(projects.libraries.core)
    implementation(libs.androidx.annotationjvm)
    implementation(libs.serialization.json)

    testCommonDependencies(libs)
}
