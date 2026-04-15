/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.pushproviders.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.annotations.ApplicationContext
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

interface FirebaseTokenDeleter {
    /**
     * Deletes the current Firebase token.
     */
    suspend fun delete()
}

@ContributesBinding(AppScope::class)
class DefaultFirebaseTokenDeleter(
    @ApplicationContext private val context: Context,
    private val isPlayServiceAvailable: IsPlayServiceAvailable,
) : FirebaseTokenDeleter {
    override suspend fun delete() {
        // 'app should always check the device for a compatible Google Play services APK before accessing Google Play services features'
        isPlayServiceAvailable.checkAvailableOrThrow()

        ensureFirebaseAppIsInitialized()

        suspendCoroutine { continuation ->
            try {
                FirebaseMessaging.getInstance().deleteToken()
                    .addOnSuccessListener {
                        continuation.resume(Unit)
                    }
                    .addOnFailureListener { e ->
                        Timber.e(e, "## deleteFirebaseToken() : failed")
                        continuation.resumeWithException(e)
                    }
            } catch (e: Throwable) {
                Timber.e(e, "## deleteFirebaseToken() : failed")
                continuation.resumeWithException(e)
            }
        }
    }

    private fun ensureFirebaseAppIsInitialized() {
        if (FirebaseApp.getApps(context).isEmpty()) {
            Timber.w("FirebaseApp is not initialized, initializing manually")
            try {
                val options = FirebaseOptions.Builder()
                    .setApplicationId(getStringResourceByName("google_app_id"))
                    .setProjectId(getStringResourceByName("project_id"))
                    .setApiKey(getStringResourceByName("google_api_key"))
                    .setGcmSenderId(getStringResourceByName("gcm_defaultSenderId"))
                    .build()
                FirebaseApp.initializeApp(context, options)
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize FirebaseApp manually")
            }
        }
    }

    private fun getStringResourceByName(name: String): String {
        val resId = context.resources.getIdentifier(name, "string", context.packageName)
        return if (resId != 0) {
            context.getString(resId)
        } else {
            ""
        }
    }
}
