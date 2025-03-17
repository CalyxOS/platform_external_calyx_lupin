/*
 * SPDX-FileCopyrightText: 2022 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.googleKsp)
    alias(libs.plugins.googleHilt)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "org.calyxos.lupin.installer"
    compileSdk = rootProject.extra["sdk"] as Int

    defaultConfig {
        applicationId = "org.calyxos.lupin.installer"
        minSdk = 33
        targetSdk = rootProject.extra["sdk"] as Int
        versionCode = 1
        versionName = "0.1"
    }

    signingConfigs {
        create("aosp") {
            // Generated from the AOSP testkey:
            // https://android.googlesource.com/platform/build/+/refs/tags/android-11.0.0_r29/target/product/security/testkey.pk8
            keyAlias = "testkey"
            keyPassword = "testkey"
            storeFile = file("../shared/testkey.jks")
            storePassword = "testkey"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("aosp")
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("aosp")
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        lintConfig = file("../shared/lint.xml")
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.lifecycle.service)

    ksp(libs.google.hilt.compiler)

    implementation(libs.google.hilt.android)
    implementation(libs.coil.compose)
}

apply("${rootProject.rootDir}/gradle/ktlint.gradle")
