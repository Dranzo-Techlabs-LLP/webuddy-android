/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.clarivaauth

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.libraries.architecture.AsyncData

open class ClarivaAuthStateProvider : PreviewParameterProvider<ClarivaAuthState> {
    override val values: Sequence<ClarivaAuthState>
        get() = sequenceOf(
            // Landing state: sign in, empty.
            aClarivaAuthState(),
            aClarivaAuthState(
                email = "jane@example.com",
                password = "a-good-password",
            ),
            aClarivaAuthState(
                email = "jane@example.com",
                password = "a-good-password",
                authAction = AsyncData.Loading(),
            ),
            // Sign-up mode is the taller layout - worth previewing for overflow.
            aClarivaAuthState(
                mode = ClarivaAuthMode.SignUp,
                email = "jane@example.com",
                password = "a-good-password",
                name = "Jane Doe",
                username = "jane",
            ),
            // Handle is free - the positive availability hint must render.
            aClarivaAuthState(
                mode = ClarivaAuthMode.SignUp,
                email = "jane@example.com",
                password = "a-good-password",
                name = "Jane Doe",
                username = "jane",
                usernameAvailability = UsernameAvailability.Available,
            ),
            // Handle already taken - blocks submit.
            aClarivaAuthState(
                mode = ClarivaAuthMode.SignUp,
                email = "jane@example.com",
                password = "a-good-password",
                name = "Jane Doe",
                username = "jane",
                usernameAvailability = UsernameAvailability.Taken,
            ),
            // Invalid handle - the inline error under the field must render.
            aClarivaAuthState(
                mode = ClarivaAuthMode.SignUp,
                email = "jane@example.com",
                password = "a-good-password",
                name = "Jane Doe",
                username = "-nope!",
            ),
            aClarivaAuthState(
                email = "jane@example.com",
                password = "a-good-password",
                authAction = AsyncData.Failure(Exception("Incorrect email or password.")),
            ),
        )
}

fun aClarivaAuthState(
    mode: ClarivaAuthMode = ClarivaAuthMode.SignIn,
    email: String = "",
    password: String = "",
    name: String = "",
    username: String = "",
    usernameAvailability: UsernameAvailability = UsernameAvailability.Unknown,
    emailAvailability: EmailAvailability = EmailAvailability.Unknown,
    isConsultant: Boolean = false,
    productionApplicationName: String = "Clariva",
    version: String = "26.01.3",
    isAddingAccount: Boolean = false,
    isGoogleSignInConfigured: Boolean = true,
    googleRolePrompt: GoogleRolePrompt? = null,
    forgotPassword: ForgotPasswordState? = null,
    authAction: AsyncData<Unit> = AsyncData.Uninitialized,
) = ClarivaAuthState(
    mode = mode,
    email = email,
    password = password,
    name = name,
    username = username,
    usernameAvailability = usernameAvailability,
    emailAvailability = emailAvailability,
    isConsultant = isConsultant,
    productionApplicationName = productionApplicationName,
    version = version,
    isAddingAccount = isAddingAccount,
    isGoogleSignInConfigured = isGoogleSignInConfigured,
    googleRolePrompt = googleRolePrompt,
    forgotPassword = forgotPassword,
    authAction = authAction,
    eventSink = {},
)
