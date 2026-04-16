/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.network.wallet

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class WalletResponse(
    @SerialName("current_hold") val currentHold: JsonElement? = null,
    @SerialName("max_credits") val maxCredits: JsonElement? = null,
    @SerialName("Webuddy_name") val webuddyName: String? = null,
)

@Serializable
data class WalletCreateRequest(
    @SerialName("name") val name: String,
    @SerialName("Webuddy_name") val webuddyName: String,
)

@Serializable
data class WalletUpdateRequest(
    @SerialName("max_credits") val maxCredits: Int
)

@Serializable
data class RechargeWalletRequest(
    @SerialName("userId") val userId: String,
    @SerialName("amount") val amount: Double,
)

@Serializable
data class CreateOrderResponse(
    @SerialName("keyId") val keyId: String,
    @SerialName("orderId") val orderId: String,
    @SerialName("amountInPaise") val amountInPaise: Long,
    @SerialName("currency") val currency: String,
    @SerialName("transactionId") val transactionId: String,
)

@Serializable
data class VerifyPaymentRequest(
    @SerialName("userId") val userId: String,
    @SerialName("transactionId") val transactionId: String,
    @SerialName("razorpay_order_id") val razorpayOrderId: String,
    @SerialName("razorpay_payment_id") val razorpayPaymentId: String,
    @SerialName("razorpay_signature") val razorpaySignature: String,
)

@Serializable
data class VerifyPaymentResponse(
    @SerialName("success") val success: Boolean,
    @SerialName("newBalance") val newBalance: Double,
    @SerialName("transactionId") val transactionId: String,
)

@Serializable
data class Transaction(
    @SerialName("id") val id: String? = null,
    @SerialName("userId") val userId: String,
    @SerialName("amount") val amount: String,
    @SerialName("type") val type: String,
    @SerialName("status") val status: String,
    @SerialName("metaData") val metaData: JsonElement? = null,
    @SerialName("createdAt") val createdAt: String? = null,
)

@Serializable
data class TransactionHistoryResponse(
    @SerialName("data") val data: List<Transaction>,
    @SerialName("total") val total: Int? = null,
    @SerialName("page") val page: Int,
    @SerialName("pageSize") val pageSize: Int,
    @SerialName("totalPages") val totalPages: Int,
)

@Serializable
data class InitiateHoldRequest(
    @SerialName("clientId") val clientId: String,
    @SerialName("consultantId") val consultantId: String,
)
