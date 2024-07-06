/*
 * SPDX-FileCopyrightText: 2022 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

buildscript {
    extra.apply {
        set("sdk", 34)
        set("compose_compiler_version", "2.0.0")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.googleKsp) apply false
    alias(libs.plugins.googleHilt) apply false
    alias(libs.plugins.ktlint) apply false
}
