/*
 * SPDX-FileCopyrightText: Copyright 2022 The Calyx Institute
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.fdroid.lupin.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fdroid.lupin.ui.theme.LupinTheme

@Composable
fun SuwPage(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int,
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SuwPage(
        modifier = modifier,
        icon = {
            Image(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                alignment = Alignment.BottomStart,
                contentScale = ContentScale.Inside,
            )
        },
        title = title,
        subtitle = subtitle,
        content = content,
    )
}

@Composable
fun SuwPage(
    modifier: Modifier = Modifier,
    icon: (@Composable ColumnScope.() -> Unit)?,
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colors.background,
    ) {
        Column(Modifier.padding(top = 72.dp, start = 40.dp, end = 40.dp, bottom = 48.dp)) {
            if (icon != null) icon()
            Text(
                text = title,
                fontSize = 34.sp,
                modifier = Modifier.padding(top = 16.dp),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 17.sp,
                    modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
                )
            }
            content()
        }
    }
}

@Composable
@Preview(showBackground = true)
fun SuwPagePreview() {
    LupinTheme {
        SuwPage(
            icon = android.R.drawable.ic_dialog_info,
            title = LoremIpsum(3).values.first(),
            subtitle = LoremIpsum(12).values.first(),
        ) {
            Text(LoremIpsum(128).values.first())
        }
    }
}

@Composable
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
fun SuwPagePreviewNight() {
    LupinTheme {
        SuwPage(
            icon = android.R.drawable.ic_dialog_info,
            title = LoremIpsum(3).values.first(),
            subtitle = LoremIpsum(12).values.first(),
        ) {
            Text(LoremIpsum(128).values.first())
        }
    }
}
