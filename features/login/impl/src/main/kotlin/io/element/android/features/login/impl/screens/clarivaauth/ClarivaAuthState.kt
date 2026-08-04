/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.clarivaauth

import io.element.android.libraries.architecture.AsyncData

enum class ClarivaAuthMode {
    SignUp,
    SignIn,
}

/**
 * The role of this Google identity is still unknown, so it must be chosen before
 * sign-in can complete. [idToken] is kept so the choice can be submitted without
 * re-prompting Google.
 *
 * Raised for a brand-new account, and for the rare existing row whose role was
 * never recorded - a returning user with a role set is never asked.
 */
data class GoogleRolePrompt(
    val idToken: String,
    val email: String?,
    val name: String?,
    /** The account already exists; only the role is missing. */
    val isExisting: Boolean = false,
)

/** Live result of the server-side handle lookup on the sign-up form. */
enum class UsernameAvailability {
    /** Nothing typed yet, or the handle is not well-formed enough to check. */
    Unknown,
    Checking,
    Available,
    Taken,
}

data class ClarivaAuthState(
    val mode: ClarivaAuthMode,
    val email: String,
    val password: String,
    val name: String,
    /** Unique handle chosen at sign-up; becomes @username:clarivahub.com. */
    val username: String,
    val usernameAvailability: UsernameAvailability,
    val isConsultant: Boolean,
    /** Shown in the welcome message, e.g. "the fastest Clariva ever". */
    val productionApplicationName: String,
    /** App version, shown at the foot of the landing page as the old welcome screen did. */
    val version: String,
    /**
     * True when at least one session already exists, i.e. the user is adding a
     * second account rather than landing on the app's welcome screen. Only then
     * is there anywhere to navigate back to.
     */
    val isAddingAccount: Boolean,
    /** False when no Google server client ID is compiled in; the button then explains itself. */
    val isGoogleSignInConfigured: Boolean,
    /**
     * Non-null while a brand-new Google user is being asked to pick a role. The
     * account does not exist yet at this point - choosing is what creates it.
     */
    val googleRolePrompt: GoogleRolePrompt?,
    val authAction: AsyncData<Unit>,
    val eventSink: (ClarivaAuthEvents) -> Unit,
) {
    val isLoading: Boolean get() = authAction is AsyncData.Loading

    /** The handle as it will actually exist: the server lowercases it. */
    val normalizedUsername: String get() = username.trim().lowercase()

    /** True when the handle is well-formed enough to be worth asking the server about. */
    val isUsernameWellFormed: Boolean
        get() = username.isNotEmpty() && USERNAME_REGEX.matches(normalizedUsername)

    /** Null when acceptable; otherwise the reason, shown under the field. */
    val usernameError: String?
        get() = when {
            username.isEmpty() -> null // don't nag before they have typed
            normalizedUsername.length < MIN_USERNAME_LENGTH -> "At least $MIN_USERNAME_LENGTH characters"
            normalizedUsername.length > MAX_USERNAME_LENGTH -> "At most $MAX_USERNAME_LENGTH characters"
            !USERNAME_REGEX.matches(normalizedUsername) ->
                "Use letters, numbers, dots, underscores or hyphens; start with a letter or number"
            usernameAvailability == UsernameAvailability.Taken -> "That username is already taken"
            else -> null
        }

    val nameError: String?
        get() = if (name.isNotEmpty() && name.trim().length < MIN_NAME_LENGTH) {
            "Please enter your name"
        } else {
            null
        }

    private val credentialsValid: Boolean
        get() = when (mode) {
            // Sign-in accepts an email OR a username, so requiring '@' here
            // would leave the button permanently disabled for username sign-in.
            ClarivaAuthMode.SignIn -> email.length >= MIN_USERNAME_LENGTH &&
                password.length >= MIN_PASSWORD_LENGTH
            ClarivaAuthMode.SignUp -> email.contains('@') &&
                email.length >= 5 &&
                password.length >= MIN_PASSWORD_LENGTH
        }

    val submitEnabled: Boolean
        get() = !isLoading && credentialsValid && when (mode) {
            ClarivaAuthMode.SignIn -> true
            // Sign-up additionally requires a name and a well-formed handle that
            // is not already known to be taken. An unfinished/failed availability
            // check does NOT block submit - the server is the real gate.
            ClarivaAuthMode.SignUp ->
                name.trim().length >= MIN_NAME_LENGTH &&
                    isUsernameWellFormed &&
                    usernameAvailability != UsernameAvailability.Taken
        }

    companion object {
        // Must match the API's @MinLength(8) or the server rejects with a 400.
        const val MIN_PASSWORD_LENGTH = 8
        const val MIN_NAME_LENGTH = 2
        const val MIN_USERNAME_LENGTH = 3
        const val MAX_USERNAME_LENGTH = 30

        /** Kept in sync with the API's USERNAME_PATTERN and Matrix localpart rules. */
        val USERNAME_REGEX = Regex("^[a-z0-9][a-z0-9._-]{2,29}$")
    }
}
