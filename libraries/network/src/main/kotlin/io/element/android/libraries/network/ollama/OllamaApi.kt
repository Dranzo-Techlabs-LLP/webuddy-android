/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.network.ollama

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

interface OllamaApi {
    @POST("/api/generate")
    suspend fun generate(
        @Body request: OllamaGenerateRequest
    ): OllamaGenerateResponse
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
    @EncodeDefault val stream: Boolean = false
)

@Serializable
data class OllamaGenerateResponse(
    val model: String,
    val response: String,
    val done: Boolean
)
