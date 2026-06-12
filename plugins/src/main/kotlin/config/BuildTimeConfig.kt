/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package config

object BuildTimeConfig {
    const val APPLICATION_ID = "com.dranzo.clariva"
    const val APPLICATION_NAME = "Clariva"
    const val GOOGLE_APP_ID_RELEASE = "1:912726360885:android:d097de99a4c23d2700427c"
    const val GOOGLE_APP_ID_DEBUG = "1:912726360885:android:def0a4e454042e9b00427c"
    const val GOOGLE_APP_ID_NIGHTLY = "1:912726360885:android:e17435e0beb0303000427c"

    // Reversed-DNS base for the OIDC login redirect scheme (login_redirect_scheme).
    // MUST reverse-map to a host equal to or under URL_WEBSITE's host, or matrix.org's
    // auth service rejects dynamic client registration with invalid_redirect_uri.
    // com.dranzo.clariva reverses to clariva.dranzo.com — under dranzo.com. Build types
    // append .debug / .nightly, which stay under the same host (debug.clariva.dranzo.com).
    val METADATA_HOST_REVERSED: String? = "com.dranzo.clariva"

    // OIDC client metadata shown on the homeserver's consent page (account.matrix.org).
    // Without these, the build falls back to element.io branding — Element logo and
    // "(element.io)" on the "Continue to Clariva?" page.
    // Constraint (enforced by matrix-authentication-service): URL_LOGO / URL_ACCEPTABLE_USE /
    // URL_POLICY must be on the SAME HOST as URL_WEBSITE.
    val URL_WEBSITE: String? = "https://dranzo.com"
    val URL_LOGO: String? = "https://dranzo.com/brand/clariva.svg"
    val URL_COPYRIGHT: String? = null
    val URL_ACCEPTABLE_USE: String? = "https://dranzo.com/terms"
    val URL_PRIVACY: String? = null
    val URL_POLICY: String? = "https://dranzo.com/privacy"
    val SERVICES_MAPTILER_BASE_URL: String? = null
    val SERVICES_MAPTILER_APIKEY: String? = null
    val SERVICES_MAPTILER_LIGHT_MAPID: String? = null
    val SERVICES_MAPTILER_DARK_MAPID: String? = null
    val SERVICES_POSTHOG_HOST: String? = null
    val SERVICES_POSTHOG_APIKEY: String? = null
    val SERVICES_SENTRY_DSN: String? = null
    val SERVICES_SENTRY_DSN_RUST: String? = null
    val BUG_REPORT_URL: String? = null
    val BUG_REPORT_APP_NAME: String? = null

    const val PUSH_CONFIG_INCLUDE_FIREBASE = true
    const val PUSH_CONFIG_INCLUDE_UNIFIED_PUSH = true
}
