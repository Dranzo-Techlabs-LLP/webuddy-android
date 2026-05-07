/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.wallet.impl

import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.network.wallet.CreateOrderResponse
import io.element.android.features.wallet.api.WalletTransaction

data class WalletState(
    val credits: Int?,
    val rechargeAction: AsyncAction<CreateOrderResponse>,
    val paymentVerificationAction: AsyncAction<Unit>,
    val transactions: List<WalletTransaction> = emptyList(),
    val isLoadingTransactions: Boolean = false,
    val canLoadMoreTransactions: Boolean = false,
    /** Recharge button is hidden when the user is a consultant (consultants receive credits, they don't buy them). */
    val isConsultant: Boolean = false,
    val eventSink: (WalletEvents) -> Unit
)
