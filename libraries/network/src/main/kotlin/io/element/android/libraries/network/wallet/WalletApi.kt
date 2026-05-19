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
    )

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
    )

    @GET("v1/pending-holds/exists")
    suspend fun checkHoldExists(
        @Query("clientId") clientId: String,
        @Query("consultantId") consultantId: String
    ): HoldExistsResponse

    @GET("v1/pending-holds/status")
    suspend fun getPendingHoldStatus(
        @Query("clientId") clientId: String,
        @Query("consultantId") consultantId: String
    ): PendingHoldStatusResponse

    @POST("v1/refund/request")
    suspend fun requestRefund(
        @Body request: RefundRequest
    ): RefundResponse

    @POST("v1/refund/approve")
    suspend fun approveRefund(
        @Body request: ApproveRefundRequest
    ): GenericRefundResponse

    @POST("v1/refund/reject")
    suspend fun rejectRefund(
        @Body request: RejectRefundRequest
    ): GenericRefundResponse

    /**
     * Bulk lookup: which clients currently have an open refund request directed at
     * [consultantId]? Returns matrix IDs only — the caller already knows which rooms
     * those map to in the chat list.
     */
    @GET("v1/refund/pending-for-consultant")
    suspend fun pendingRefundsForConsultant(
        @Query("consultantId") consultantId: String,
    ): PendingRefundsForConsultantResponse

    /**
     * Atomic claim for the in-room Matrix push-notification on an auto-approved refund.
     * Server returns `{ claimed: true }` exactly once across all callers — the first
     * app to claim wins the right to post the m.room.message; later calls return
     * `{ claimed: false }` and the caller MUST skip the send so the room doesn't get
     * duplicate notifications.
     */
    @POST("v1/refund/auto-approval-claim/{id}")
    suspend fun claimAutoApprovalNotification(
        @Path("id") refundRequestId: String,
        @Body request: ClaimAutoApprovalRequest,
    ): ClaimAutoApprovalResponse
}
