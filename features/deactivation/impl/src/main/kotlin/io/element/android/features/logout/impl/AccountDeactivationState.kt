/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.logout.impl

import android.os.Parcelable
import io.element.android.libraries.architecture.AsyncAction
import kotlinx.parcelize.Parcelize

data class AccountDeactivationState(
    val deactivateFormState: DeactivateFormState,
    val accountDeactivationAction: AsyncAction<Unit>,
    /**
     * Google accounts have NO password - the Clariva password field is
     * meaningless for them, so they re-authorise with Google instead. Null while
     * we are still asking the server which kind of account this is.
     */
    val isGoogleAccount: Boolean?,
    /** True once Google has handed back a fresh ID token proving ownership. */
    val isGoogleAuthorized: Boolean,
    val eventSink: (AccountDeactivationEvents) -> Unit,
) {
    val submitEnabled: Boolean
        get() = accountDeactivationAction is AsyncAction.Uninitialized && when (isGoogleAccount) {
            // Still loading the account type: do not let them submit blind.
            null -> false
            true -> isGoogleAuthorized
            false -> deactivateFormState.password.isNotEmpty()
        }
}

@Parcelize
data class DeactivateFormState(
    val eraseData: Boolean,
    val password: String
) : Parcelable {
    companion object {
        val Default = DeactivateFormState(false, "")
    }
}
