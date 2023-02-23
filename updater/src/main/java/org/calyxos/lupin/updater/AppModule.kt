/*
 * SPDX-FileCopyrightText: 2023 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.updater

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.calyxos.lupin.updater.BuildConfig.VERSION_NAME
import org.fdroid.download.HttpManager

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun provideHttpManager(
        @ApplicationContext context: Context,
    ): HttpManager {
        return HttpManager("${context.getString(R.string.app_name)} $VERSION_NAME")
    }
}
