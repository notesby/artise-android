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
    namespace = "co.artise.android.accountdeletion"
}

setupDependencyInjection()

dependencies {
    implementation(projects.libraries.di)
    implementation(libs.androidx.corektx)
    implementation(projects.services.toolbox.api)

    testCommonDependencies(libs)
    testImplementation(projects.services.toolbox.test)
}
