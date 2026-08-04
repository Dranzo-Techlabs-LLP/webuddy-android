/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.network.wallet

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import timber.log.Timber

/**
 * Clariva account sign-up / sign-in.
 *
 * Flow: authenticate against the Clariva API, then exchange the returned JWT for
 * a Matrix access token. The Matrix password is generated server-side, used once
 * and discarded - it never reaches the device.
 *
 * The JWT is held in memory only. It is needed to mint a Matrix session at
 * sign-in time; once the Matrix session is imported, the app is driven by
 * SessionStore. Persisting it would be required for silent token re-issue after
 * a process restart, which this does not yet do.
 */
@Inject
@SingleIn(AppScope::class)
class ClarivaAuthService(
    private val clarivaAuthApi: ClarivaAuthApi,
) {
    private var token: String? = null

    var lastAuthenticatedUser: ClarivaUser? = null
        private set

    data class Credentials(
        val matrixUserId: String,
        val accessToken: String,
        val deviceId: String,
        /**
         * Server-held passphrase for this account's Matrix secret storage.
         *
         * Applying it after import gives the device the cross-signing and key
         * backup secrets, so it becomes trusted and can read history WITHOUT the
         * user verifying anything. Null on an older API - the session still
         * works, it just has no access to messages sent before it existed.
         */
        val recoveryPassphrase: String? = null,
    )

    /** Google sign-in either completes, or stops to ask a new user for their role. */
    sealed interface GoogleAuthOutcome {
        data class SignedIn(val credentials: Credentials) : GoogleAuthOutcome
        data class RoleRequired(
            val email: String?,
            val name: String?,
            /** The account exists and only its role is missing. */
            val isExisting: Boolean = false,
        ) : GoogleAuthOutcome
    }

    suspend fun signUpWithEmail(
        email: String,
        password: String,
        name: String,
        username: String,
        isConsultant: Boolean,
    ): Result<Credentials> = runAuth {
        clarivaAuthApi.register(
            ClarivaRegisterRequest(
                email = email.trim(),
                password = password,
                name = name.trim(),
                username = username.trim().lowercase(),
                isConsultant = if (isConsultant) 1 else 0,
            )
        )
    }

    suspend fun signInWithEmail(email: String, password: String): Result<Credentials> = runAuth {
        clarivaAuthApi.login(ClarivaLoginRequest(email = email.trim(), password = password))
    }

    /**
     * Either signs in, or reports that a brand-new user must pick a role first.
     *
     * [isConsultant] is null on the first attempt so the server can answer
     * "needsRole" rather than defaulting everyone to a normal client.
     */
    suspend fun signInWithGoogle(
        idToken: String,
        isConsultant: Boolean?,
        isSignUp: Boolean = false,
    ): Result<GoogleAuthOutcome> {
        return try {
            val response = clarivaAuthApi.google(
                ClarivaGoogleRequest(
                    idToken = idToken,
                    isConsultant = isConsultant?.let { if (it) 1 else 0 },
                    // Only the sign-up screen opts in to the stricter behaviour;
                    // sign-in must keep working for accounts that already exist.
                    mode = if (isSignUp) "signup" else null,
                )
            )

            if (response.needsRole == true) {
                return Result.success(
                    GoogleAuthOutcome.RoleRequired(
                        email = response.email,
                        name = response.name,
                        isExisting = response.isExisting == true,
                    )
                )
            }

            val token = response.token
            if (token == null) {
                return Result.failure(ClarivaAuthException("Clariva did not return a session. Please try again."))
            }
            this.token = token
            lastAuthenticatedUser = response.user

            val session = clarivaAuthApi.matrixSession("Bearer $token")
            Timber.d("Google auth OK, matrix session for ${session.userId}")
            Result.success(
                GoogleAuthOutcome.SignedIn(
                    Credentials(
                        matrixUserId = session.userId,
                        accessToken = session.accessToken,
                        deviceId = session.deviceId,
                        recoveryPassphrase = session.recoveryPassphrase,
                    )
                )
            )
        } catch (e: HttpException) {
            Timber.w(e, "Google auth failed with HTTP ${e.code()}")
            Result.failure(ClarivaAuthException(mapHttpError(e), e))
        } catch (e: Exception) {
            Timber.e(e, "Google auth failed")
            Result.failure(ClarivaAuthException("Could not reach Clariva. Check your connection and try again.", e))
        }
    }

    /** Does this account deactivate with a password, or with Google re-auth? */
    suspend fun isGoogleAccount(matrixUserId: String): Boolean? {
        return try {
            clarivaAuthApi.reauthMethod(matrixUserId).isGoogleAccount
        } catch (e: Exception) {
            Timber.d(e, "Could not determine re-auth method for $matrixUserId")
            null
        }
    }

    /**
     * Permanently deactivate. Exactly one proof is required: [password] for an
     * email account, [googleIdToken] for a Google one.
     */
    suspend fun deactivateAccount(
        matrixUserId: String,
        password: String? = null,
        googleIdToken: String? = null,
        eraseData: Boolean = false,
    ): Result<Unit> {
        return try {
            val res = clarivaAuthApi.deactivate(
                ClarivaDeactivateRequest(
                    matrixUserId = matrixUserId,
                    password = password,
                    googleIdToken = googleIdToken,
                    eraseData = eraseData,
                )
            )
            if (res.deactivated) Result.success(Unit)
            else Result.failure(ClarivaAuthException("Could not deactivate the account. Please try again."))
        } catch (e: HttpException) {
            Timber.w(e, "Deactivation failed with HTTP ${e.code()}")
            val message = serverMessage(e) ?: when (e.code()) {
                401 -> "Could not verify your identity. Please try again."
                400 -> "Please re-authorise before deactivating."
                else -> "Could not deactivate the account (error ${e.code()})."
            }
            Result.failure(ClarivaAuthException(message, e))
        } catch (e: Exception) {
            Timber.e(e, "Deactivation failed")
            Result.failure(ClarivaAuthException("Could not reach Clariva. Check your connection and try again.", e))
        }
    }

    /**
     * Is this handle free? Returns null when the check could not be made (offline,
     * server down) so the caller can stay silent rather than claim it is taken.
     */
    suspend fun isUsernameAvailable(username: String): ClarivaUsernameAvailability? {
        return try {
            clarivaAuthApi.usernameAvailable(username.trim().lowercase())
        } catch (e: Exception) {
            Timber.d(e, "Username availability check failed for $username")
            null
        }
    }

    fun clear() {
        token = null
        lastAuthenticatedUser = null
    }

    private suspend fun runAuth(block: suspend () -> ClarivaAuthResponse): Result<Credentials> {
        return try {
            val auth = block()
            token = auth.token
            lastAuthenticatedUser = auth.user

            val session = clarivaAuthApi.matrixSession("Bearer ${auth.token}")
            Timber.d("Clariva auth OK, matrix session for ${session.userId}")

            Result.success(
                Credentials(
                    matrixUserId = session.userId,
                    accessToken = session.accessToken,
                    deviceId = session.deviceId,
                    recoveryPassphrase = session.recoveryPassphrase,
                )
            )
        } catch (e: HttpException) {
            Timber.w(e, "Clariva auth failed with HTTP ${e.code()}")
            Result.failure(ClarivaAuthException(mapHttpError(e), e))
        } catch (e: Exception) {
            Timber.e(e, "Clariva auth failed")
            Result.failure(ClarivaAuthException("Could not reach Clariva. Check your connection and try again.", e))
        }
    }

    private fun mapHttpError(e: HttpException): String {
        // 400 and 409 carry an actionable, user-facing reason from the API - which
        // username is taken, which field is malformed - so prefer it over a
        // generic string. A 409 is no longer only "email in use".
        if (e.code() == 400 || e.code() == 409) {
            serverMessage(e)?.let { return it }
        }
        return when (e.code()) {
            400 -> "Please check the details you entered and try again."
            401 -> "Incorrect email or password."
            409 -> "That email or username is already taken."
            in 500..599 -> "Clariva is having trouble right now. Please try again shortly."
            else -> "Sign-in failed (error ${e.code()})."
        }
    }

    /** Pull `message` out of Nest's error envelope; it may be a string or a list. */
    private fun serverMessage(e: HttpException): String? = runCatching {
        val raw = e.response()?.errorBody()?.string().orEmpty()
        if (raw.isBlank()) return@runCatching null
        val message = Json.parseToJsonElement(raw).jsonObject["message"] ?: return@runCatching null
        when (message) {
            is JsonArray -> message.mapNotNull { it.jsonPrimitive.contentOrNull }
                .joinToString("\n")
                .ifBlank { null }
            else -> message.jsonPrimitive.contentOrNull
        }
    }.getOrNull()
}

class ClarivaAuthException(
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
