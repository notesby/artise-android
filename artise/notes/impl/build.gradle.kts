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
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "co.artise.android.notes.impl"
}

setupDependencyInjection()

dependencies {
    api(projects.artise.notes.api)
    implementation(projects.libraries.architecture)
    implementation(projects.libraries.core)
    implementation(projects.libraries.designsystem)
    implementation(projects.libraries.sessionStorage.api)
    implementation(projects.libraries.uiStrings)
    implementation(projects.features.enterprise.api)
    implementation(projects.libraries.di)
    implementation(projects.libraries.encryptedDb)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.network)
    implementation(projects.services.toolbox.api)
    implementation(libs.network.okhttp.okhttp)
    implementation(platform(libs.network.okhttp.bom))
    implementation(libs.serialization.json)
    implementation(libs.timber)
    implementation(libs.sqldelight.driver.android)
    implementation(libs.sqldelight.coroutines)
    implementation(libs.sqlcipher)
    implementation(libs.sqlite)
    // Markdown for reading notes (BSD-2-Clause).
    implementation("org.commonmark:commonmark:0.30.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.30.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.30.0")
    implementation("org.commonmark:commonmark-ext-task-list-items:0.30.0")
    implementation("org.commonmark:commonmark-ext-autolink:0.30.0")
    implementation(libs.androidx.compose.material.icons)

    testCommonDependencies(libs)
    testImplementation(projects.libraries.matrix.test)
    testImplementation(projects.services.toolbox.test)
    testImplementation(libs.network.mockwebserver)
    testImplementation(libs.sqldelight.driver.jvm)
    testImplementation(libs.molecule.runtime)
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
