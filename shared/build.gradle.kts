import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * SPDX-FileCopyrightText: 2023 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.googleKsp)
    alias(libs.plugins.googleHilt)
    alias(libs.plugins.ktlint)
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_21 } }

android {
    namespace = "org.calyxos.lupin"
    compileSdk = rootProject.extra["sdk"] as Int

    defaultConfig {
        minSdk = 33
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    api(libs.fdroid.download)
    api(libs.fdroid.index)

    api(libs.androidx.core)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.ui.tooling.preview)
    api(libs.androidx.activity.compose)
    api(libs.androidx.lifecycle.runtime.ktx)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.lifecycle.livedata)

    api(libs.logback.android)
    api(libs.github.microutils.kotlin.logging)

    ksp(libs.google.hilt.compiler)
    implementation(libs.google.hilt.android)

    debugApi(libs.androidx.compose.ui.tooling)
}
