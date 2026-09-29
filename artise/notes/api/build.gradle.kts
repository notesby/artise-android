/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

plugins {
    id("io.element.android-library")
}

android {
    namespace = "co.artise.android.notes.api"
}

dependencies {
    api(projects.libraries.architecture)
    api(projects.libraries.matrix.api)
}
