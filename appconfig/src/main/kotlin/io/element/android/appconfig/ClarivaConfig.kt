/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appconfig

object ClarivaConfig {
    /**
     * Base URL of the Clariva Wallet-API (auth + wallet).
     *
     * `10.0.2.2` is the host machine as seen from the Android emulator, and is
     * already allow-listed for cleartext in res/xml/network_security_config.xml.
     * Point this back at the production host before shipping a release build.
     */
    const val API_BASE_URL = "http://10.0.2.2:3000/"

    /** Production value, kept here so switching back is a one-line change. */
    const val PRODUCTION_API_BASE_URL = "https://wallet.dranzo.com/"

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
