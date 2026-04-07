/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.wallet.impl

import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.network.wallet.RechargeOrderResponse
import io.element.android.libraries.network.wallet.WalletTransaction

data class WalletState(
    val credits: Int?,
    val rechargeAction: AsyncAction<RechargeOrderResponse>,
    val paymentVerificationAction: AsyncAction<Unit>,
    val transactions: List<WalletTransaction> = emptyList(),
    val isLoadingTransactions: Boolean = false,
    val eventSink: (WalletEvents) -> Unit
)
