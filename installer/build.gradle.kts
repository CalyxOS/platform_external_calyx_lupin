/*
 * SPDX-FileCopyrightText: 2022 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.googleKsp)
    alias(libs.plugins.googleHilt)
    alias(libs.plugins.ktlint)
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
    composeOptions {
        kotlinCompilerExtensionVersion = rootProject.extra["compose_compiler_version"] as String
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
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
