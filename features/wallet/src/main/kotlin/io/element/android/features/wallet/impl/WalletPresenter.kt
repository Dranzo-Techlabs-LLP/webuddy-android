/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.wallet.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.network.wallet.RechargeOrderResponse
import io.element.android.libraries.network.wallet.WalletService
import kotlinx.coroutines.launch
import timber.log.Timber

class WalletPresenter @Inject constructor(
    private val walletService: WalletService,
    private val matrixClient: MatrixClient,
) : Presenter<WalletState> {

    @Composable
    override fun present(): WalletState {
        val coroutineScope = rememberCoroutineScope()
        val credits by walletService.credits.collectAsState()
        val rechargeAction = remember { mutableStateOf<AsyncAction<RechargeOrderResponse>>(AsyncAction.Uninitialized) }
        val paymentVerificationAction = remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }

        fun handleEvent(event: WalletEvents) {
            when (event) {
                is WalletEvents.Recharge -> {
                    Timber.d("Handling Recharge event for amount: ${event.amount}")
                    coroutineScope.launch {
                        rechargeAction.runCatchingUpdatingState {
                            val userId = matrixClient.sessionId.value
                            Timber.d("Creating recharge order for user: $userId")
                            walletService.createRechargeOrder(
                                userId = userId,
                                amount = event.amount
                            ).also {
                                Timber.d("Recharge order created successfully: $it")
                            }
                        }
                    }
                }
                is WalletEvents.PaymentCompleted -> {
                    Timber.d("Handling PaymentCompleted event: $event")
                    coroutineScope.launch {
                        paymentVerificationAction.runCatchingUpdatingState {
                            walletService.verifyPayment(
                                userId = matrixClient.sessionId.value,
                                transactionId = event.transactionId,
                                orderId = event.orderId,
                                paymentId = event.paymentId,
                                signature = event.signature
                            ).also {
                                Timber.d("Payment verified successfully")
                            }
                            Unit
                        }
                    }
                }
                WalletEvents.ClearAction -> {
                    Timber.d("Clearing actions")
                    rechargeAction.value = AsyncAction.Uninitialized
                    paymentVerificationAction.value = AsyncAction.Uninitialized
                }
            }
        }

        return WalletState(
            credits = credits,
            rechargeAction = rechargeAction.value,
            paymentVerificationAction = paymentVerificationAction.value,
            eventSink = ::handleEvent
        )
    }
}
