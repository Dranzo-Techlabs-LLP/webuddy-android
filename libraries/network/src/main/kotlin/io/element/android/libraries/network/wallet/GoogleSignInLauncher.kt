/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.network.wallet

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.element.android.appconfig.ClarivaConfig
import timber.log.Timber

/** Outcome of asking Credential Manager for a Google ID token. */
sealed interface GoogleSignInResult {
    data class Success(val idToken: String) : GoogleSignInResult
    /** User dismissed the sheet - not an error, show nothing. */
    data object Cancelled : GoogleSignInResult
    data class Failure(val message: String) : GoogleSignInResult
}

/**
 * Obtains a Google ID token via Credential Manager.
 *
 * Lives here rather than in a feature module because two features need it:
 * sign-in, and re-authorising to deactivate a Google account.
 *
 * The token is verified SERVER-SIDE (signature, issuer, audience,
 * email_verified); the app never trusts it locally and never asserts its own
 * email.
 *
 * [ClarivaConfig.GOOGLE_SERVER_CLIENT_ID] must be the **Web/server** OAuth
 * client ID: Android ID tokens carry that as their `aud`, not the Android one.
 */
suspend fun requestGoogleIdToken(context: Context): GoogleSignInResult {
    if (!ClarivaConfig.isGoogleSignInConfigured) {
        return GoogleSignInResult.Failure("Google sign-in is not configured in this build.")
    }

    // Always offer EVERY Google account on the device, not just the ones already
    // authorised for Clariva. Filtering to authorised accounts first shows a
    // one-tap sheet for the last-used account, which silently hides the other
    // accounts a user may want to sign in with (and gives them no way to pick).
    return requestWith(context, filterByAuthorizedAccounts = false)
        ?: GoogleSignInResult.Failure(
            "No Google account is available on this device. Add one in Settings and try again."
        )
}

/**
 * Returns null when this pass found no usable credential, so the caller can fall
 * back to a broader query. Cancellation and real errors return a value.
 */
private suspend fun requestWith(
    context: Context,
    filterByAuthorizedAccounts: Boolean,
): GoogleSignInResult? {
    val option = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
        .setServerClientId(ClarivaConfig.GOOGLE_SERVER_CLIENT_ID)
        // Ask explicitly rather than auto-selecting a single account: this also
        // creates accounts and authorises deletion, so a silent pick would be
        // surprising in both cases.
        .setAutoSelectEnabled(false)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(option)
        .build()

    return try {
        val response = CredentialManager.create(context).getCredential(context, request)
        val credential = response.credential
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            Timber.w("Unexpected credential type from Credential Manager: ${credential.type}")
            return GoogleSignInResult.Failure("Unexpected credential type from Google.")
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        if (idToken.isNullOrBlank()) {
            GoogleSignInResult.Failure("Google did not return an ID token.")
        } else {
            GoogleSignInResult.Success(idToken)
        }
    } catch (e: NoCredentialException) {
        // No matching account for this pass - let the caller widen the query.
        Timber.d("No Google credential (filterByAuthorizedAccounts=$filterByAuthorizedAccounts)")
        null
    } catch (e: GetCredentialCancellationException) {
        Timber.d("Google sign-in cancelled by the user")
        GoogleSignInResult.Cancelled
    } catch (e: GetCredentialException) {
        // Misconfiguration lands here: wrong client ID, package/SHA-1 not
        // registered, or Play Services missing. Surface it rather than hiding it.
        Timber.e(e, "Google sign-in failed")
        GoogleSignInResult.Failure(
            e.errorMessage?.toString()?.takeIf { it.isNotBlank() }
                ?: "Google sign-in failed. Please try again."
        )
    } catch (e: Exception) {
        Timber.e(e, "Google sign-in failed unexpectedly")
        GoogleSignInResult.Failure("Google sign-in failed. Please try again.")
    }
}
