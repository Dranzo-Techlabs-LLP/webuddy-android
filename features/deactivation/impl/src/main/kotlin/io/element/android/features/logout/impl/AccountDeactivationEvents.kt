/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.logout.impl

sealed interface AccountDeactivationEvents {
    data class SetEraseData(val eraseData: Boolean) : AccountDeactivationEvents
    data class SetPassword(val password: String) : AccountDeactivationEvents
    data class DeactivateAccount(val isRetry: Boolean) : AccountDeactivationEvents
    data object CloseDialogs : AccountDeactivationEvents

    /** Google-account users tap "Authorise with Google" instead of typing a password. */
    data object AuthorizeWithGoogle : AccountDeactivationEvents

    /** Credential Manager returned a fresh ID token proving account ownership. */
    data class GoogleAuthorized(val idToken: String) : AccountDeactivationEvents

    data class GoogleAuthorizationFailed(val message: String) : AccountDeactivationEvents
}
