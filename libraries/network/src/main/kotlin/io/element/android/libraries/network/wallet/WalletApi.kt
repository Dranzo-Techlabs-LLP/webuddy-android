/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.network.wallet

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface WalletApi {
    @POST("v1/users/")
    suspend fun createUser(
        @Body request: WalletCreateRequest
    ): WalletResponse

    @GET("v1/users/{userId}/")
    suspend fun getWalletBalance(
        @Path("userId", encoded = true) userId: String
    ): WalletResponse

    @PATCH("v1/users/{userId}/")
    suspend fun updateWallet(
        @Path("userId", encoded = true) userId: String,
        @Body request: WalletUpdateRequest
    ): WalletResponse
}
