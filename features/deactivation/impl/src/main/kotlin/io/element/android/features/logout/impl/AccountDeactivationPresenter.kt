/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.logout.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.network.wallet.ClarivaAuthService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Account deactivation.
 *
 * Deliberately does NOT use `matrixClient.deactivateAccount`. That performs
 * Matrix User-Interactive Auth with the account's Matrix password - which no
 * Clariva user knows, because it is machine-generated server-side, used once and
 * discarded. It would therefore fail for email accounts too, not just Google
 * ones. Instead the Clariva API re-authenticates the user (password, or a fresh
 * Google ID token) and deactivates through the Synapse admin API.
 */
@Inject
class AccountDeactivationPresenter(
    private val matrixClient: MatrixClient,
    private val clarivaAuthService: ClarivaAuthService,
) : Presenter<AccountDeactivationState> {
    @Composable
    override fun present(): AccountDeactivationState {
        val localCoroutineScope = rememberCoroutineScope()
        val action: MutableState<AsyncAction<Unit>> = remember {
            mutableStateOf(AsyncAction.Uninitialized)
        }

        val formState = remember { mutableStateOf(DeactivateFormState.Default) }
        var googleIdToken by remember { mutableStateOf<String?>(null) }

        val matrixUserId = remember { matrixClient.sessionId.value }

        // Which re-auth does this account need? Unknown (null) until it answers;
        // the submit button stays disabled until then so nobody submits blind.
        val isGoogleAccount by produceState<Boolean?>(initialValue = null, matrixUserId) {
            value = clarivaAuthService.isGoogleAccount(matrixUserId)
        }

        fun handleEvent(event: AccountDeactivationEvents) {
            when (event) {
                is AccountDeactivationEvents.SetEraseData -> {
                    updateFormState(formState) { copy(eraseData = event.eraseData) }
                }
                is AccountDeactivationEvents.SetPassword -> {
                    updateFormState(formState) { copy(password = event.password) }
                }
                // The view launches Credential Manager; this exists so the button
                // has a sink even though the work happens at the node layer.
                AccountDeactivationEvents.AuthorizeWithGoogle -> Unit
                is AccountDeactivationEvents.GoogleAuthorized -> {
                    googleIdToken = event.idToken
                    action.value = AsyncAction.Uninitialized
                }
                is AccountDeactivationEvents.GoogleAuthorizationFailed -> {
                    googleIdToken = null
                    action.value = AsyncAction.Failure(Exception(event.message))
                }
                is AccountDeactivationEvents.DeactivateAccount ->
                    if (action.value.isConfirming() || event.isRetry) {
                        localCoroutineScope.deactivateAccount(
                            matrixUserId = matrixUserId,
                            formState = formState.value,
                            isGoogleAccount = isGoogleAccount == true,
                            googleIdToken = googleIdToken,
                            action = action,
                        )
                    } else {
                        action.value = AsyncAction.ConfirmingNoParams
                    }
                AccountDeactivationEvents.CloseDialogs -> {
                    action.value = AsyncAction.Uninitialized
                }
            }
        }

        return AccountDeactivationState(
            deactivateFormState = formState.value,
            accountDeactivationAction = action.value,
            isGoogleAccount = isGoogleAccount,
            isGoogleAuthorized = googleIdToken != null,
            eventSink = ::handleEvent,
        )
    }

    private fun updateFormState(formState: MutableState<DeactivateFormState>, updateLambda: DeactivateFormState.() -> DeactivateFormState) {
        formState.value = updateLambda(formState.value)
    }

    private fun CoroutineScope.deactivateAccount(
        matrixUserId: String,
        formState: DeactivateFormState,
        isGoogleAccount: Boolean,
        googleIdToken: String?,
        action: MutableState<AsyncAction<Unit>>,
    ) = launch {
        suspend {
            clarivaAuthService.deactivateAccount(
                matrixUserId = matrixUserId,
                // Send exactly one proof so the server cannot be confused about
                // which it should trust.
                password = if (isGoogleAccount) null else formState.password,
                googleIdToken = if (isGoogleAccount) googleIdToken else null,
                eraseData = formState.eraseData,
            ).getOrThrow()

            // The Clariva account and its Matrix account are gone; drop the local
            // session so the app returns to the sign-in screen.
            matrixClient.logout(userInitiated = true, ignoreSdkError = true)
            Unit
        }.runCatchingUpdatingState(action)
    }
}
