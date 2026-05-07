/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.wallet.api

/**
 * Data class representing a recharge order response from the backend.
 */
data class RechargeOrderResponse(val orderId: String, val amountInPaise: Int, val keyId: String)

/**
 * Data class representing a wallet transaction.
 *
 * - [type] is the ledger direction: CREDIT, DEBIT, BONUS.
 * - [source] is the business cause: RECHARGE, HOLD, HOLD_SETTLED, REFUND, REFERRAL.
 *   May be null for legacy rows.
 */
data class WalletTransaction(
    val id: String,
    val amount: Double,
    val status: String,
    val type: String,
    val source: String?,
    val date: String,
)
