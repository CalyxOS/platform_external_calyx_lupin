/*
 * SPDX-FileCopyrightText: Copyright 2022 The Calyx Institute
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.fdroid.lupin.ui

import android.content.res.Configuration
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.fdroid.lupin.AppItem
import org.fdroid.lupin.R
import org.fdroid.lupin.ui.theme.LupinTheme

@Composable
fun InstallPage(items: List<AppItem>) {
    SuwPage(
        icon = R.drawable.ic_launcher_foreground,
        title = stringResource(R.string.install_page_title),
        subtitle = stringResource(R.string.install_page_subtitle),
    ) {
        LazyColumn {
            items(items) { item ->
                AppItemRow(item)
            }
        }
    }
}

@Composable
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
fun InstallPagePreview() {
    LupinTheme {
        InstallPage(listOf(getRandomAppItem(), getRandomAppItem(), getRandomAppItem()))
    }
}
