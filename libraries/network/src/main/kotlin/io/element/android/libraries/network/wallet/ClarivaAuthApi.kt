/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.network.wallet

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Clariva account API.
 *
 * Sign-up creates the Clariva account first; the server derives the Matrix ID
 * from the account's immutable UUID and provisions the homeserver account. The
 * device never sees or handles a Matrix password.
 */
interface ClarivaAuthApi {
    @POST("v1/auth/register")
    suspend fun register(@Body request: ClarivaRegisterRequest): ClarivaAuthResponse

    @POST("v1/auth/login")
    suspend fun login(@Body request: ClarivaLoginRequest): ClarivaAuthResponse

    /** Live availability check while the user is still typing a handle. */
    @GET("v1/auth/username-available")
    suspend fun usernameAvailable(@Query("username") username: String): ClarivaUsernameAvailability

    /**
     * Returns `needsRole = true` (and creates nothing) when a brand-new Google
     * user has not chosen a role yet; the client asks and re-posts.
     */
    @POST("v1/auth/google")
    suspend fun google(@Body request: ClarivaGoogleRequest): ClarivaGoogleResponse

    /** Password field or "Re-authorise with Google" button for deactivation? */
    @GET("v1/auth/reauth-method")
    suspend fun reauthMethod(@Query("matrixUserId") matrixUserId: String): ClarivaReauthMethod

    /** Deactivate permanently. Authorised by the proof in the body, not a token. */
    @POST("v1/auth/deactivate")
    suspend fun deactivate(@Body request: ClarivaDeactivateRequest): ClarivaDeactivateResponse

    /**
     * Exchange the Clariva session JWT for a Matrix access token.
     * Authorised by the caller's own token - there is no path parameter and no
     * shared secret, so a client can only ever obtain its own session.
     */
    @POST("v1/auth/matrix-session")
    suspend fun matrixSession(@Header("Authorization") bearer: String): ClarivaMatrixSessionResponse
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ClarivaRegisterRequest(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String,
    // Display name - mandatory. Without it Matrix shows the raw localpart.
    @SerialName("name") val name: String,
    // Unique handle; becomes @username:clarivahub.com. A clash returns 409.
    @SerialName("username") val username: String,
    // Always emit: the API validates this as 0 or 1 and kotlinx-serialization
    // would otherwise drop it when it equals the default.
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("isConsultant") val isConsultant: Int = 0,
)

@Serializable
data class ClarivaLoginRequest(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String,
)

@Serializable
data class ClarivaGoogleRequest(
    @SerialName("idToken") val idToken: String,
    // NULL on the first call so the server can answer "needsRole" instead of
    // silently defaulting every Google user to a normal client. Omitted from the
    // JSON when null, which is exactly what the server checks for.
    @SerialName("isConsultant") val isConsultant: Int? = null,
    // "signup" makes an already-registered email a hard error telling the user to
    // sign in, instead of silently signing them in from the sign-up screen.
    // Null (omitted) keeps the permissive sign-in behaviour.
    @SerialName("mode") val mode: String? = null,
)

/**
 * Either a completed sign-in, or a request for the role of a brand-new user.
 * Both arrive as HTTP 200, so the fields are nullable and `needsRole` decides.
 */
@Serializable
data class ClarivaGoogleResponse(
    @SerialName("needsRole") val needsRole: Boolean? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("name") val name: String? = null,
    // True when the row already exists and only its role is missing, so the
    // prompt can say "finish setting up" rather than "create your account".
    @SerialName("isExisting") val isExisting: Boolean? = null,
    @SerialName("token") val token: String? = null,
    @SerialName("user") val user: ClarivaUser? = null,
)

@Serializable
data class ClarivaReauthMethod(
    @SerialName("isGoogleAccount") val isGoogleAccount: Boolean,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ClarivaDeactivateRequest(
    @SerialName("matrixUserId") val matrixUserId: String,
    @SerialName("password") val password: String? = null,
    @SerialName("googleIdToken") val googleIdToken: String? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("eraseData") val eraseData: Boolean = false,
)

@Serializable
data class ClarivaDeactivateResponse(
    @SerialName("deactivated") val deactivated: Boolean = false,
)

@Serializable
data class ClarivaAuthResponse(
    @SerialName("token") val token: String,
    @SerialName("user") val user: ClarivaUser,
)

@Serializable
data class ClarivaUser(
    @SerialName("id") val id: String,
    @SerialName("email") val email: String? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("matrixUserId") val matrixUserId: String? = null,
    @SerialName("isConsultant") val isConsultant: Int? = null,
    @SerialName("isGoogleAccount") val isGoogleAccount: Boolean? = null,
)

@Serializable
data class ClarivaUsernameAvailability(
    @SerialName("available") val available: Boolean,
    @SerialName("reason") val reason: String? = null,
)

@Serializable
data class ClarivaMatrixSessionResponse(
    @SerialName("userId") val userId: String,
    @SerialName("accessToken") val accessToken: String,
    @SerialName("deviceId") val deviceId: String,
)
