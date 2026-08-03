/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.clarivaauth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.appconfig.ClarivaConfig
import io.element.android.appconfig.OnBoardingConfig
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.api.auth.external.ExternalSession
import io.element.android.libraries.network.wallet.ClarivaAuthService
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Clariva account sign-up / sign-in.
 *
 * The user never sees a Matrix password. We authenticate against the Clariva API,
 * exchange the resulting JWT for a Matrix access token, and import that as a
 * session. Navigation is deliberately absent: RootFlowNode observes SessionStore
 * and switches to the logged-in graph on its own once the session lands.
 */
@Inject
class ClarivaAuthPresenter(
    private val clarivaAuthService: ClarivaAuthService,
    private val authenticationService: MatrixAuthenticationService,
    private val buildMeta: BuildMeta,
    private val sessionStore: SessionStore,
) : Presenter<ClarivaAuthState> {
    @Composable
    override fun present(): ClarivaAuthState {
        val coroutineScope = rememberCoroutineScope()

        // This screen is the app's landing page, so returning users are the
        // common case - default to signing in rather than signing up.
        var mode by rememberSaveable { mutableStateOf(ClarivaAuthMode.SignIn) }
        var email by rememberSaveable { mutableStateOf("") }
        var password by rememberSaveable { mutableStateOf("") }
        var name by rememberSaveable { mutableStateOf("") }
        var username by rememberSaveable { mutableStateOf("") }
        var isConsultant by rememberSaveable { mutableStateOf(false) }
        var authAction by remember { mutableStateOf<AsyncData<Unit>>(AsyncData.Uninitialized) }
        var googleRolePrompt by remember { mutableStateOf<GoogleRolePrompt?>(null) }

        val isAddingAccount by produceState(initialValue = false) {
            value = sessionStore.numberOfSessions() > 0
        }

        // Debounced availability lookup. Keyed on the normalised handle so mere
        // case changes do not re-query, and cancelled on every keystroke so only
        // the value the user settled on is checked.
        val normalizedUsername = username.trim().lowercase()
        val isWellFormed = normalizedUsername.isNotEmpty() &&
            ClarivaAuthState.USERNAME_REGEX.matches(normalizedUsername)
        var usernameAvailability by remember { mutableStateOf(UsernameAvailability.Unknown) }

        LaunchedEffect(normalizedUsername, isWellFormed, mode) {
            if (mode != ClarivaAuthMode.SignUp || !isWellFormed) {
                usernameAvailability = UsernameAvailability.Unknown
                return@LaunchedEffect
            }
            usernameAvailability = UsernameAvailability.Checking
            delay(USERNAME_CHECK_DEBOUNCE_MS)
            val result = clarivaAuthService.isUsernameAvailable(normalizedUsername)
            usernameAvailability = when {
                // Network failure: stay quiet rather than wrongly claim it is taken.
                result == null -> UsernameAvailability.Unknown
                result.available -> UsernameAvailability.Available
                else -> UsernameAvailability.Taken
            }
        }

        fun handleEvent(event: ClarivaAuthEvents) {
            when (event) {
                is ClarivaAuthEvents.SetEmail -> email = event.email.trim()
                is ClarivaAuthEvents.SetPassword -> password = event.password
                is ClarivaAuthEvents.SetName -> name = event.name
                // Store EXACTLY what was typed. Rewriting the text here (trim /
                // lowercase / truncate) desynchronises the Android IME's composing
                // region, which made the field lose focus mid-word and drop the
                // rest of the input into the previous field. Normalisation happens
                // at submit time and in the preview instead.
                is ClarivaAuthEvents.SetUsername -> username = event.username
                is ClarivaAuthEvents.SetIsConsultant -> isConsultant = event.isConsultant
                ClarivaAuthEvents.ToggleMode -> {
                    mode = if (mode == ClarivaAuthMode.SignUp) ClarivaAuthMode.SignIn else ClarivaAuthMode.SignUp
                    authAction = AsyncData.Uninitialized
                }
                ClarivaAuthEvents.ClearError -> authAction = AsyncData.Uninitialized
                ClarivaAuthEvents.Submit -> {
                    authAction = AsyncData.Loading()
                    coroutineScope.launch {
                        val result = when (mode) {
                            ClarivaAuthMode.SignUp -> clarivaAuthService.signUpWithEmail(
                                email = email,
                                password = password,
                                name = name,
                                username = username,
                                isConsultant = isConsultant,
                            )
                            ClarivaAuthMode.SignIn -> clarivaAuthService.signInWithEmail(
                                email = email,
                                password = password,
                            )
                        }
                        authAction = result.fold(
                            onSuccess = { importSession(it) },
                            onFailure = { AsyncData.Failure(it) },
                        )
                    }
                }
                is ClarivaAuthEvents.SubmitGoogleIdToken -> {
                    authAction = AsyncData.Loading()
                    // Captured now: the dialog may outlive a mode toggle, and the
                    // follow-up call must match the screen the user actually used.
                    val startedFromSignUp = mode == ClarivaAuthMode.SignUp
                    coroutineScope.launch {
                        // isConsultant = null: let the server tell us whether the
                        // role is still unknown for this identity.
                        authAction = handleGoogleOutcome(
                            result = clarivaAuthService.signInWithGoogle(
                                idToken = event.idToken,
                                isConsultant = null,
                                isSignUp = startedFromSignUp,
                            ),
                            idToken = event.idToken,
                            isSignUp = startedFromSignUp,
                            onRoleRequired = { googleRolePrompt = it },
                        )
                    }
                }
                is ClarivaAuthEvents.ConfirmGoogleRole -> {
                    val prompt = googleRolePrompt ?: return@handleEvent
                    googleRolePrompt = null
                    authAction = AsyncData.Loading()
                    coroutineScope.launch {
                        authAction = handleGoogleOutcome(
                            result = clarivaAuthService.signInWithGoogle(
                                idToken = prompt.idToken,
                                isConsultant = event.isConsultant,
                                // Never "signup" here: the server already told us
                                // this identity needs a role, so re-asserting
                                // sign-up would reject the very account we are
                                // finishing setup for.
                                isSignUp = false,
                            ),
                            idToken = prompt.idToken,
                            isSignUp = false,
                            // The server already asked once; a second prompt would
                            // mean something is wrong, so treat it as an error.
                            onRoleRequired = null,
                        )
                    }
                }
                ClarivaAuthEvents.CancelGoogleRole -> {
                    googleRolePrompt = null
                    authAction = AsyncData.Uninitialized
                }
                is ClarivaAuthEvents.GoogleSignInFailed -> {
                    authAction = AsyncData.Failure(Exception(event.message))
                }
            }
        }

        return ClarivaAuthState(
            mode = mode,
            email = email,
            password = password,
            name = name,
            username = username,
            usernameAvailability = usernameAvailability,
            isConsultant = isConsultant,
            productionApplicationName = buildMeta.productionApplicationName,
            version = buildMeta.versionName,
            isAddingAccount = isAddingAccount,
            isGoogleSignInConfigured = ClarivaConfig.isGoogleSignInConfigured,
            googleRolePrompt = googleRolePrompt,
            authAction = authAction,
            eventSink = ::handleEvent,
        )
    }

    /**
     * Turn Clariva credentials into a live Matrix session.
     *
     * setHomeserver() must run first - importCreatedSession() errors with
     * "You need to call setHomeserver() first" otherwise. It is called once per
     * attempt because it rotates (and deletes) the previous session path.
     */
    /**
     * Turn a Google auth result into screen state: either import the session, or
     * raise the role prompt for a brand-new user.
     */
    private suspend fun handleGoogleOutcome(
        result: Result<ClarivaAuthService.GoogleAuthOutcome>,
        idToken: String,
        isSignUp: Boolean,
        onRoleRequired: ((GoogleRolePrompt) -> Unit)?,
    ): AsyncData<Unit> {
        return result.fold(
            onSuccess = { outcome ->
                when (outcome) {
                    is ClarivaAuthService.GoogleAuthOutcome.SignedIn ->
                        importSession(outcome.credentials)
                    is ClarivaAuthService.GoogleAuthOutcome.RoleRequired -> {
                        if (onRoleRequired == null) {
                            AsyncData.Failure(Exception("Could not complete Google sign-up. Please try again."))
                        } else {
                            onRoleRequired(
                                GoogleRolePrompt(
                                    idToken = idToken,
                                    email = outcome.email,
                                    name = outcome.name,
                                    isExisting = outcome.isExisting,
                                )
                            )
                            // Leave the button idle while the dialog is up.
                            AsyncData.Uninitialized
                        }
                    }
                }
            },
            onFailure = { AsyncData.Failure(it) },
        )
    }

    private suspend fun importSession(credentials: ClarivaAuthService.Credentials): AsyncData<Unit> {
        return try {
            authenticationService.setHomeserver(OnBoardingConfig.DEFAULT_HOMESERVER_URL).getOrThrow()

            authenticationService.importCreatedSession(
                ExternalSession(
                    userId = credentials.matrixUserId,
                    deviceId = credentials.deviceId,
                    accessToken = credentials.accessToken,
                    refreshToken = null,
                    homeserverUrl = MATRIX_HOMESERVER_URL,
                )
            ).getOrThrow()

            // NOTE: mandatory session verification is skipped for Clariva sessions in
            // DefaultFtueService (canSkipVerification), not here. Clariva accounts are
            // provisioned server-side and the user never holds a Matrix password or
            // recovery key, so on a second device none of Element's verify routes are
            // usable and the FTUE would otherwise wall the user off at "Confirm your
            // identity". We deliberately do NOT open the session-preferences DataStore
            // from this AppScope presenter - doing so races/collides with the
            // session-scoped DataStore for the same file ("multiple DataStores active
            // for the same file") on a re-login. Messages remain end-to-end encrypted;
            // the device is simply not cross-signed.
            Timber.d("Clariva session imported for ${credentials.matrixUserId}")
            AsyncData.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to import Clariva session")
            AsyncData.Failure(e)
        }
    }

    private companion object {
        /** Client-Server API endpoint - the matrix. subdomain, not the delegating apex. */
        const val MATRIX_HOMESERVER_URL = "https://matrix.clarivahub.com"

        /** Long enough that a normal typist triggers one lookup per handle, not one per key. */
        const val USERNAME_CHECK_DEBOUNCE_MS = 450L
    }
}
