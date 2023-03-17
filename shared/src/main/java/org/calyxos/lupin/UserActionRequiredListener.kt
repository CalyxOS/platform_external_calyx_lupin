/*
 * SPDX-FileCopyrightText: 2023 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin

import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION
import androidx.annotation.UiThread

fun interface UserActionRequiredListener {
    /**
     * Called when user confirmation is required for the installation of the [packageName].
     * Usually, you want to start an activity with the given [intent].
     * However, you need to ensure that your app is allowed to launch an activity at this time.
     * You can use [ApkInstaller.canStartActivity] for this.
     *
     * @param packageName the package name of the package that requires user confirmation.
     * @param sessionId The ID of the [PackageInstaller.SessionInfo] or -1, if not known.
     * @param intent The [Intent] we can start to prompt user confirmation in the UI.
     *
     * @return true if you did show a prompt to the user.
     * This is typically the case, when the app is in the foreground
     * and user confirmation can be performed right now.
     * We return an [InstallResult] with our own [STATUS_WAITING_FOR_USER_ACTION] in this case,
     * because the [PackageInstaller] API does not always return a result, so we can't wait for one.
     * If false is returned, the call will return an [InstallResult]
     * with [STATUS_PENDING_USER_ACTION].
     */
    @UiThread
    fun onUserConfirmationRequired(packageName: String, sessionId: Int, intent: Intent): Boolean
}
