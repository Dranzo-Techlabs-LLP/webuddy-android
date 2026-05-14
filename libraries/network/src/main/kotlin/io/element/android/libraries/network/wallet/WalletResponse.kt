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
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class WalletResponse(
    @SerialName("current_hold") val currentHold: JsonElement? = null,
    @SerialName("max_credits") val maxCredits: JsonElement? = null,
    @SerialName("Webuddy_name") val webuddyName: String? = null,
    @SerialName("isConsultant") val isConsultant: Int? = null,
)

@Serializable
data class WalletCreateRequest(
    @SerialName("name") val name: String,
    @SerialName("Webuddy_name") val webuddyName: String,
    @SerialName("isConsultant") val isConsultant: Int = 0,
)

@Serializable
data class WalletUpdateRequest(
    @SerialName("max_credits") val maxCredits: Int? = null,
    @SerialName("isConsultant") val isConsultant: Int? = null,
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
    /** Business source: RECHARGE, HOLD, HOLD_SETTLED, REFUND, REFERRAL. Used to render a human-readable label. */
    @SerialName("source") val source: String? = null,
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

@Serializable
data class HoldExistsResponse(
    @SerialName("exists") val exists: Boolean,
)

@Serializable
data class PendingHoldStatusResponse(
    @SerialName("exists") val exists: Boolean = false,
    @SerialName("isActive") val isActive: Int = 1,         // DB: tinyint(1), 1=active 0=inactive
    @SerialName("isRefundActive") val isRefundActive: Int = 1, // DB: tinyint, 1=refund active 0=closed
    @SerialName("refund_status") val refundStatus: String = "none", // DB enum: 'none','requested','approved','rejected'
    @SerialName("pendingHoldId") val pendingHoldId: JsonElement? = null, // API-level field, not a DB column
    @SerialName("id") val id: JsonElement? = null, // Fallback to DB-level id field
    @SerialName("clientId") val clientId: String? = null,
    @SerialName("consultantId") val consultantId: String? = null,
    @SerialName("refundRequestId") val refundRequestId: JsonElement? = null,
) {
    val holdIdString: String? get() {
        val element = pendingHoldId ?: id ?: return null
        return try {
            element.jsonPrimitive.content
        } catch (_: Exception) {
            element.toString().removeSurrounding("\"")
        }
    }

    val refundRequestIdString: String? get() = refundRequestId?.let {
        try {
            it.jsonPrimitive.content
        } catch (_: Exception) {
            it.toString().removeSurrounding("\"")
        }
    }
}

@Serializable
data class ApproveRefundRequest(
    @SerialName("refundRequestId") val refundRequestId: String,
    @SerialName("pendingHoldId") val pendingHoldId: String,
)

@Serializable
data class RejectRefundRequest(
    @SerialName("refundRequestId") val refundRequestId: String,
)

@Serializable
data class GenericRefundResponse(
    @SerialName("message") val message: String? = null,
    @SerialName("amount") val amount: Double? = null,
)

@Serializable
data class RefundRequest(
    @SerialName("clientId") val clientId: String,
    @SerialName("consultantId") val consultantId: String,
    @SerialName("pendingHoldId") val pendingHoldId: String
)

@Serializable
data class RefundResponse(
    @SerialName("success") val success: Boolean,
    @SerialName("message") val message: String? = null
)

@Serializable
data class PendingRefundsForConsultantResponse(
    @SerialName("clientIds") val clientIds: List<String> = emptyList(),
)
