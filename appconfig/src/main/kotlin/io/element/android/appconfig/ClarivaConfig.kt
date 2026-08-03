/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appconfig

object ClarivaConfig {
    /** Production Wallet-API. */
    const val PRODUCTION_API_BASE_URL = "https://wallet.dranzo.com/"

    /**
     * The host machine as seen from the Android emulator. Already allow-listed
     * for cleartext in res/xml/network_security_config.xml; production is https
     * and needs no such exception.
     */
    const val LOCAL_EMULATOR_API_BASE_URL = "http://10.0.2.2:3000/"

    /**
     * Base URL of the Clariva Wallet-API (auth + wallet).
     *
     * Swap to [LOCAL_EMULATOR_API_BASE_URL] to run against a laptop-hosted API
     * in the emulator; anything shipped to a device must stay on production.
     */
    const val API_BASE_URL = PRODUCTION_API_BASE_URL

    /**
     * Google OAuth **Web/server** client ID from Google Cloud Console.
     *
     * Android Google Sign-In issues ID tokens whose `aud` is the web client ID,
     * not the Android one, so this is the value to paste here. It must also be
     * listed in the API's GOOGLE_CLIENT_IDS.
     *
     * Empty = Google sign-in is unconfigured; the button reports that plainly
     * rather than failing with an opaque error.
     */
    const val GOOGLE_SERVER_CLIENT_ID = "462109274571-5qsno88m3vu5gijggbvccej3v8tt1qbe.apps.googleusercontent.com"

    val isGoogleSignInConfigured: Boolean get() = GOOGLE_SERVER_CLIENT_ID.isNotEmpty()
}
