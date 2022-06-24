/*
 * SPDX-FileCopyrightText: Copyright 2022 The Calyx Institute
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.fdroid.lupin.ui

import android.app.Activity.RESULT_FIRST_USER
import android.app.Activity.RESULT_OK
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

val horizontalMargin = 40.dp
fun Modifier.suwPageModifier() = padding(
    top = horizontalMargin,
    start = horizontalMargin,
    end = horizontalMargin,
)
const val RESULT_SKIP = RESULT_FIRST_USER
const val RESULT_NEXT = RESULT_OK

@Composable
fun SuwPage(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SuwPage(
        modifier = modifier,
        header = {
            SuwHeader(
                icon = icon,
                title = title,
                subtitle = subtitle,
            )
        },
        content = content,
    )
}

@Composable
fun SuwPage(
    header: (@Composable ColumnScope.() -> Unit),
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colors.background,
    ) {
        Column(modifier = modifier.suwPageModifier()) {
            header()
            content()
        }
    }
}

@Composable
fun SuwHeader(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    SuwHeader(
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
    )
}

@Composable
fun SuwHeader(
    icon: (@Composable ColumnScope.() -> Unit)?,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier) {
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
