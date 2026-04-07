import extension.setupDependencyInjection

/*
 * Copyright (c) 2024 New Vector Ltd
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

plugins {
    id("io.element.android-compose-library")
    id("kotlin-parcelize")
}

android {
    namespace = "io.element.android.features.wallet"
}

setupDependencyInjection()

dependencies {
    implementation(projects.features.preferences.api)
    implementation(projects.libraries.architecture)
    implementation(projects.libraries.designsystem)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.network)
    implementation(projects.libraries.di)
    implementation(libs.razorpay.checkout)
    implementation(libs.metro.runtime)
}
