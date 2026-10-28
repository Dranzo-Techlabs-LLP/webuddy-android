/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.network.presence

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.network.interceptors.DynamicHttpLoggingInterceptor
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

/** A user's Matrix presence as reported by the homeserver. */
data class UserPresence(
    val isOnline: Boolean,
    /** Wall-clock time (ms) of the user's last activity, or null when the server doesn't say. */
    val lastActiveTs: Long?,
)

/**
 * Minimal client for the Matrix presence API (`/_matrix/client/v3/presence/{userId}/status`).
 *
 * The Rust SDK exposes no presence API, and simplified sliding sync never marks anyone online,
 * so Clariva publishes its own presence explicitly (see PresenceHeartbeat) and reads a DM
 * partner's presence on demand. Everything is best effort: failures return false / null.
 */
@SingleIn(AppScope::class)
@Inject
class MatrixPresenceService(
    okHttpClient: OkHttpClient,
    private val sessionStore: SessionStore,
    private val dispatchers: CoroutineDispatchers,
) {
    // These requests carry the Matrix access token. The shared client's logging interceptor prints
    // whole requests, headers included, at debug log level, so they use a copy without it.
    private val httpClient: OkHttpClient = okHttpClient.newBuilder()
        .apply { interceptors().removeAll { it is DynamicHttpLoggingInterceptor } }
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    /** Publishes the presence of the account [sessionId]. Returns true when the server accepted it. */
    suspend fun setPresence(sessionId: String, online: Boolean): Boolean = withContext(dispatchers.io) {
        val body = json.encodeToString(SetPresenceBody.serializer(), SetPresenceBody(if (online) ONLINE else OFFLINE))
        val request = presenceRequest(sessionId, userId = sessionId)
            ?.put(body.toRequestBody(JSON_MEDIA_TYPE))
            ?.build()
            ?: return@withContext false
        runRequest(request) { _, _ -> true } ?: false
    }

    /** Reads [userId]'s presence as seen by the account [sessionId], or null when unavailable. */
    suspend fun getPresence(sessionId: String, userId: String): UserPresence? = withContext(dispatchers.io) {
        val request = presenceRequest(sessionId, userId)?.get()?.build() ?: return@withContext null
        runRequest(request) { body, receivedAt ->
            val status = json.decodeFromString(PresenceStatus.serializer(), body)
            UserPresence(
                isOnline = status.presence == ONLINE,
                lastActiveTs = status.lastActiveAgo?.let { receivedAt - it },
            )
        }
    }

    private suspend fun presenceRequest(sessionId: String, userId: String): Request.Builder? {
        val session = sessionStore.getSession(sessionId) ?: return null
        val url = session.homeserverUrl.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments("_matrix/client/v3/presence")
            ?.addPathSegment(userId)
            ?.addPathSegment("status")
            ?.build()
            ?: return null
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${session.accessToken}")
    }

    /** Runs [request]; [onSuccess] gets the body and the local time the response arrived. */
    private fun <T> runRequest(request: Request, onSuccess: (body: String, receivedAt: Long) -> T): T? {
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // 403 = no shared room; also returned while presence is disabled server-side for PUT.
                    Timber.d("Presence ${request.method} returned HTTP ${response.code}")
                    return null
                }
                onSuccess(response.body.string(), System.currentTimeMillis())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Presence ${request.method} failed")
            null
        }
    }

    private companion object {
        const val ONLINE = "online"
        const val OFFLINE = "offline"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

@Serializable
private data class SetPresenceBody(
    @SerialName("presence") val presence: String,
)

@Serializable
private data class PresenceStatus(
    @SerialName("presence") val presence: String? = null,
    @SerialName("last_active_ago") val lastActiveAgo: Long? = null,
)
