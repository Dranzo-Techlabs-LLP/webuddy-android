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
import retrofit2.http.Query

interface WalletApi {
    @POST("v1/users")
    suspend fun createUser(
        @Body request: WalletCreateRequest
    ): WalletResponse

    @GET("v1/users/{Webuddy_name}")
    suspend fun getWalletBalance(
        @Path("Webuddy_name") webuddyName: String
    ): WalletResponse

    @PATCH("v1/users/{Webuddy_name}")
    suspend fun updateWallet(
        @Path("Webuddy_name") webuddyName: String,
        @Body request: WalletUpdateRequest
    ): WalletResponse

    @POST("v1/experts/bank-details")
    suspend fun postBankDetails(
        @Body request: BankDetails
    ): Unit

    @POST("v1/wallet/recharge")
    suspend fun createOrder(
        @Body request: RechargeWalletRequest
    ): CreateOrderResponse

    @POST("v1/wallet/verify")
    suspend fun verifyPayment(
        @Body request: VerifyPaymentRequest
    ): VerifyPaymentResponse

    @GET("v1/wallet/history")
    suspend fun getTransactionHistory(
        @Query("userId") userId: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int
    ): TransactionHistoryResponse

    @POST("v1/pending-holds/initiate")
    suspend fun initiateHold(
        @Body request: InitiateHoldRequest
    ): Unit
}
