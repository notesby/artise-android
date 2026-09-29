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
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "co.artise.android.notes.impl"
}

setupDependencyInjection()

dependencies {
    api(projects.artise.notes.api)
    implementation(projects.libraries.core)
    implementation(projects.libraries.di)
    implementation(projects.libraries.encryptedDb)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.network)
    implementation(projects.services.toolbox.api)
    implementation(libs.network.okhttp.okhttp)
    implementation(platform(libs.network.okhttp.bom))
    implementation(libs.serialization.json)
    implementation(libs.sqldelight.driver.android)
    implementation(libs.sqldelight.coroutines)
    implementation(libs.sqlcipher)
    implementation(libs.sqlite)

    testCommonDependencies(libs)
    testImplementation(projects.libraries.matrix.test)
    testImplementation(projects.services.toolbox.test)
    testImplementation(libs.network.mockwebserver)
    testImplementation(libs.sqldelight.driver.jvm)
}

sqldelight {
    databases {
        create("NotesDatabase") {
            packageName = "co.artise.android.notes.impl.db"
            schemaOutputDirectory = File("src/main/sqldelight/databases")
            verifyMigrations = true
        }
    }
}
