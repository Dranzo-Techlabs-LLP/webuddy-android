/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.network.wallet

import retrofit2.http.GET
import retrofit2.http.Path

interface WalletApi {
    @GET("v1/users/{userId}")
    suspend fun getWalletBalance(
        @Path("userId", encoded = true) userId: String
    ): WalletResponse
}
