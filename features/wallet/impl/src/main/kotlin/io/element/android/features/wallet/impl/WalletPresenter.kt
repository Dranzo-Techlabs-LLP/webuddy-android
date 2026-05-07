/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.wallet.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.zacsweers.metro.Inject
import io.element.android.features.wallet.api.WalletTransaction
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.network.wallet.WalletPaymentResult
import io.element.android.libraries.network.wallet.WalletService
import io.element.android.libraries.network.wallet.CreateOrderResponse
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
        val isCurrentUserConsultant by walletService.isCurrentUserConsultant.collectAsState()
        val rechargeAction = remember { mutableStateOf<AsyncAction<CreateOrderResponse>>(AsyncAction.Uninitialized) }

        val transactionsState = remember { mutableStateOf<List<WalletTransaction>>(emptyList()) }
        val isLoadingTransactions = remember { mutableStateOf(false) }
        val currentPage = remember { mutableIntStateOf(1) }
        val canLoadMore = remember { mutableStateOf(true) }

        fun fetchTransactions(page: Int, isRefresh: Boolean = false) {
            if (isLoadingTransactions.value && !isRefresh) return
            isLoadingTransactions.value = true
            coroutineScope.launch {
                val userId = matrixClient.sessionId.value
                walletService.getTransactionHistory(userId, page = page, pageSize = 10)
                    .onSuccess { response ->
                        val newTransactions = response.data.map {
                            WalletTransaction(
                                id = it.id ?: "",
                                amount = it.amount.toDoubleOrNull() ?: 0.0,
                                status = it.status,
                                type = it.type,
                                date = it.createdAt ?: ""
                            )
                        }

                        transactionsState.value = if (isRefresh) {
                            newTransactions
                        } else {
                            transactionsState.value + newTransactions
                        }
                        
                        canLoadMore.value = response.page < response.totalPages
                        currentPage.intValue = response.page
                        isLoadingTransactions.value = false
                        Timber.d("Fetched ${newTransactions.size} transactions. Total: ${transactionsState.value.size}")
                    }
                    .onFailure {
                        isLoadingTransactions.value = false
                        Timber.e(it, "Failed to fetch transactions")
                    }
            }
        }

        LaunchedEffect(Unit) {
            fetchTransactions(1, isRefresh = true)
        }

        fun handleEvent(event: WalletEvents) {
            when (event) {
                is WalletEvents.Recharge -> {
                    Timber.d("Handling Recharge event for amount: ${event.amount}")
                    coroutineScope.launch {
                        rechargeAction.runCatchingUpdatingState {
                            val userId = matrixClient.sessionId.value
                            Timber.d("Recharging wallet for user: $userId with amount: ${event.amount}")
                            walletService.createOrder(
                                userId = userId,
                                amount = event.amount.toDouble(),
                            ).getOrThrow()
                        }
                    }
                }
                is WalletEvents.PaymentCompleted -> {
                    Timber.d("Handling PaymentCompleted event: $event")
                    coroutineScope.launch {
                        val userId = matrixClient.sessionId.value
                        walletService.verifyPayment(
                            userId = userId,
                            transactionId = event.transactionId,
                            razorpayOrderId = event.orderId,
                            razorpayPaymentId = event.paymentId,
                            razorpaySignature = event.signature
                        ).onSuccess {
                            fetchTransactions(1, isRefresh = true)
                        }
                    }
                }
                WalletEvents.LoadMoreTransactions -> {
                    if (canLoadMore.value) {
                        fetchTransactions(currentPage.intValue + 1)
                    }
                }
                WalletEvents.RefreshTransactions -> {
                    fetchTransactions(1, isRefresh = true)
                }
                WalletEvents.ClearAction -> {
                    Timber.d("Clearing actions")
                    rechargeAction.value = AsyncAction.Uninitialized
                }
            }
        }

        LaunchedEffect(Unit) {
            walletService.paymentResults
                .onEach { result ->
                    Timber.d("Received payment result: $result")
                    when (result) {
                        is WalletPaymentResult.Success -> {
                            val transactionId = result.orderId?.let { walletService.getTransactionIdForOrder(it) } ?: ""
                            handleEvent(
                                WalletEvents.PaymentCompleted(
                                    transactionId = transactionId,
                                    orderId = result.orderId ?: "",
                                    paymentId = result.paymentId ?: "",
                                    signature = result.signature ?: ""
                                )
                            )
                        }
                        is WalletPaymentResult.Error -> {
                            rechargeAction.value = AsyncAction.Failure(Exception(result.message))
                        }
                    }
                }
                .launchIn(coroutineScope)
        }

        return WalletState(
            credits = credits,
            rechargeAction = rechargeAction.value,
            paymentVerificationAction = AsyncAction.Uninitialized,
            transactions = transactionsState.value,
            isLoadingTransactions = isLoadingTransactions.value,
            canLoadMoreTransactions = canLoadMore.value,
            isConsultant = isCurrentUserConsultant == true,
            eventSink = ::handleEvent
        )
    }
}
