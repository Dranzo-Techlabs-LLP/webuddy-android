/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.clarivaauth

sealed interface ClarivaAuthEvents {
    data class SetEmail(val email: String) : ClarivaAuthEvents
    data class SetPassword(val password: String) : ClarivaAuthEvents
    data class SetName(val name: String) : ClarivaAuthEvents
    data class SetUsername(val username: String) : ClarivaAuthEvents
    data class SetIsConsultant(val isConsultant: Boolean) : ClarivaAuthEvents
    data object ToggleMode : ClarivaAuthEvents
    data object Submit : ClarivaAuthEvents

    /** [idToken] comes from Google Credential Manager on the device. */
    data class SubmitGoogleIdToken(val idToken: String) : ClarivaAuthEvents

    /** New Google user picked a role in the dialog; finish creating the account. */
    data class ConfirmGoogleRole(val isConsultant: Boolean) : ClarivaAuthEvents

    /** Dismissed the role dialog without choosing - abandon the sign-up. */
    data object CancelGoogleRole : ClarivaAuthEvents

    /** Google sign-in could not produce a token (cancelled, misconfigured, no Play Services). */
    data class GoogleSignInFailed(val message: String) : ClarivaAuthEvents

    data object ClearError : ClarivaAuthEvents

    // --- Forgot / reset password (6-digit code by email) ---
    /** Open the forgot-password dialog (from the sign-in screen). */
    data object ForgotPasswordOpen : ClarivaAuthEvents
    /** Close/cancel the forgot-password dialog. */
    data object ForgotPasswordDismiss : ClarivaAuthEvents
    data class ForgotPasswordSetEmail(val email: String) : ClarivaAuthEvents
    data class ForgotPasswordSetCode(val code: String) : ClarivaAuthEvents
    data class ForgotPasswordSetNewPassword(val password: String) : ClarivaAuthEvents
    /** Step 1: request the emailed code. */
    data object ForgotPasswordRequestCode : ClarivaAuthEvents
    /** Step 2: submit the code + new password. */
    data object ForgotPasswordSubmit : ClarivaAuthEvents
}
