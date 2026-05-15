/*
 * SPDX-FileCopyrightText: 2026 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.IOException

fun interface TempFileProvider {
    @Throws(IOException::class)
    fun createTempFile(prefix: String, suffix: String): File
}

@Module
@InstallIn(SingletonComponent::class)
object TempFileProviderModule {
    @Provides
    fun provideTempFileProvider(
        @ApplicationContext context: Context,
    ): TempFileProvider {
        return TempFileProvider { prefix, suffix ->
            File.createTempFile(prefix, suffix, context.cacheDir)
        }
    }
}
