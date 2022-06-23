/*
 * SPDX-FileCopyrightText: Copyright 2022 The Calyx Institute
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.fdroid.lupin.ui

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fdroid.lupin.AppItem
import org.fdroid.lupin.R

@Composable
fun AppItemRow(item: AppItem) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            alignment = Alignment.Center,
            contentScale = ContentScale.Inside,
        )
        Column {
            Text(
                text = item.name,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = item.summary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Switch(checked = item.selected, onCheckedChange = null)
    }
}

@Composable
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
fun AppItemRowPreviewNight() {
    Surface {
        AppItemRow(getRandomAppItem())
    }
}

@Composable
@Preview(showBackground = true)
fun AppItemRowPreview() {
    Surface {
        AppItemRow(getRandomAppItem())
    }
}

@Composable
internal fun getRandomAppItem() = AppItem(
    icon = LocalContext.current.getDrawable(R.drawable.ic_launcher_foreground)!!,
    name = LoremIpsum(3).values.first(),
    summary = LoremIpsum(8).values.first(),
    selected = true,
)
