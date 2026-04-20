/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.wallet.impl

sealed interface WalletEvents {
    data class Recharge(val amount: Int) : WalletEvents
    data class PaymentCompleted(
        val transactionId: String,
        val orderId: String,
        val paymentId: String,
        val signature: String
    ) : WalletEvents
    object LoadMoreTransactions : WalletEvents
    object RefreshTransactions : WalletEvents
    object ClearAction : WalletEvents
}
