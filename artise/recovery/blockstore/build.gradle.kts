/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

import extension.setupDependencyInjection

// Artise: the recovery key vault's store in Play builds, Google's Block Store (end-to-end encrypted, restored to a new phone).
plugins {
    id("io.element.android-library")
}

android {
    namespace = "co.artise.android.recovery.blockstore"
}

setupDependencyInjection()

dependencies {
    implementation(projects.artise.recovery.api)
    implementation(projects.libraries.core)
    implementation(projects.libraries.di)
    implementation("com.google.android.gms:play-services-auth-blockstore:16.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    implementation(libs.timber)
}
