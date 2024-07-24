/*
 * SPDX-FileCopyrightText: 2022 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

buildscript {
    extra.apply {
        set("sdk", 35)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.jetbrains.kotlin.compose) apply false
    alias(libs.plugins.googleKsp) apply false
    alias(libs.plugins.googleHilt) apply false
    alias(libs.plugins.ktlint) apply false
}
