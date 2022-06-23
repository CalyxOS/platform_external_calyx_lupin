/*
 * SPDX-FileCopyrightText: Copyright 2022 The Calyx Institute
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.fdroid.lupin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat.setDecorFitsSystemWindows
import org.fdroid.lupin.ui.InstallPage
import org.fdroid.lupin.ui.getRandomAppItem
import org.fdroid.lupin.ui.theme.LupinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDecorFitsSystemWindows(window, false)
        setContent {
            LupinTheme {
                InstallPage(listOf(getRandomAppItem(), getRandomAppItem(), getRandomAppItem(), getRandomAppItem(), getRandomAppItem(), getRandomAppItem(), getRandomAppItem(), getRandomAppItem(), getRandomAppItem(),
                    getRandomAppItem(),
                    getRandomAppItem(), getRandomAppItem(), getRandomAppItem(), getRandomAppItem(),
                    getRandomAppItem()))
            }
        }
    }
}
