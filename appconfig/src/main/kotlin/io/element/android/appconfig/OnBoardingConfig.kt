/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appconfig

object OnBoardingConfig {
    /**
     * Whether the user can create an account using the app.
     * Clariva provisions accounts server-side (Wallet-API -> Synapse admin API); the
     * homeserver runs with enable_registration=false, so in-app sign-up always fails.
     */
    const val CAN_CREATE_ACCOUNT = false

    /**
     * The default homeserver URL.
     * The bare apex is deliberate: clarivahub.com serves /.well-known/matrix/client,
     * which points discovery at https://matrix.clarivahub.com and yields
     * @user:clarivahub.com identifiers.
     */
    const val DEFAULT_HOMESERVER_URL = "https://clarivahub.com"

    /**
     * The single account provider offered in onboarding, rendered as-is, so this
     * is the bare server_name rather than a URL.
     */
    const val ACCOUNT_PROVIDER = "clarivahub.com"

    /**
     * Hosts this app may connect to. Clariva is a closed deployment: users must
     * not be able to sign in to arbitrary homeservers.
     *
     * Matched by exact host equality after parsing - never by prefix or
     * `contains`, which would let "clarivahub.com.attacker.net" through.
     */
    val ALLOWED_HOMESERVER_HOSTS = setOf(
        "clarivahub.com",
        "matrix.clarivahub.com",
    )
}
