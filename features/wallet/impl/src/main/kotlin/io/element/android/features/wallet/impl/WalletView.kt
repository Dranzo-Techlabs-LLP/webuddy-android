/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.wallet.impl

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.razorpay.Checkout
import io.element.android.features.wallet.api.WalletTransaction
import io.element.android.libraries.architecture.AsyncAction
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.theme.aliasScreenTitle
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.designsystem.theme.components.Button
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.ui.text.input.KeyboardType
import io.element.android.libraries.designsystem.components.dialogs.TextFieldDialog
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.network.wallet.CreateOrderResponse
import org.json.JSONObject
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletView(
    state: WalletState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current
    var showRechargeDialog by remember { mutableStateOf(false) }
    var amountToRecharge by remember { mutableStateOf("") }

    // Handle payment flow
    LaunchedEffect(state.rechargeAction) {
        val action = state.rechargeAction
        if (action is AsyncAction.Success) {
            val orderResponse = action.data
            Timber.d("Order created successfully: $orderResponse")
            if (activity != null) {
                startPayment(
                    activity = activity,
                    orderId = orderResponse.orderId,
                    amount = orderResponse.amountInPaise.toInt(),
                    key = orderResponse.keyId
                )
            }
        }
    }

    if (showRechargeDialog) {
        TextFieldDialog(
            title = "Recharge Wallet",
            content = "Enter the number of credits to purchase.",
            placeholder = "e.g. 500",
            label = "Amount",
            value = amountToRecharge,
            onSubmit = { amountStr ->
                val amount = amountStr.toIntOrNull()
                if (amount != null && amount > 0) {
                    state.eventSink(WalletEvents.Recharge(amount))
                    showRechargeDialog = false
                    amountToRecharge = ""
                }
            },
            onDismissRequest = {
                showRechargeDialog = false
                amountToRecharge = ""
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            submitText = "Recharge"
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    BackButton(onClick = onBackClick)
                },
                title = {
                    Text(
                        modifier = Modifier.semantics {
                            heading()
                        },
                        text = "Wallet",
                        style = ElementTheme.typography.aliasScreenTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        },
        content = { padding ->
            TransactionList(
                transactions = state.transactions,
                isLoading = state.isLoadingTransactions,
                canLoadMore = state.canLoadMoreTransactions,
                onLoadMore = { state.eventSink(WalletEvents.LoadMoreTransactions) },
                credits = state.credits,
                rechargeAction = state.rechargeAction,
                showRechargeButton = !state.isConsultant,
                onRechargeClick = { showRechargeDialog = true },
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
            )
        }
    )
}

@Composable
fun TransactionList(
    transactions: List<WalletTransaction>,
    isLoading: Boolean,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    credits: Int?,
    rechargeAction: AsyncAction<CreateOrderResponse>,
    showRechargeButton: Boolean,
    onRechargeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItemsCount = listState.layoutInfo.totalItemsCount
            lastVisibleItemIndex >= totalItemsCount - 2 && canLoadMore && !isLoading
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth()
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Current Balance",
                    style = ElementTheme.typography.fontBodyLgRegular
                )
                Text(
                    text = "${credits ?: 0} credits",
                    style = ElementTheme.typography.fontHeadingLgBold
                )
                Spacer(modifier = Modifier.height(24.dp))

                if (showRechargeButton) {
                    if (rechargeAction.isLoading()) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Processing recharge...")
                    } else {
                        Button(
                            text = "Recharge Credits",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onRechargeClick
                        )
                    }
                }

                if (rechargeAction is AsyncAction.Failure) {
                    Text(
                        text = "Recharge failed: ${rechargeAction.error.message}",
                        color = ElementTheme.colors.textCriticalPrimary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Transaction History",
                    style = ElementTheme.typography.fontHeadingSmMedium,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        item {
            TransactionHeader()
            HorizontalDivider()
        }

        items(transactions) { transaction ->
            TransactionRow(transaction)
            HorizontalDivider()
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        } else if (transactions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "No transactions found")
                }
            }
        }
    }
}

@Composable
fun TransactionHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Date",
            modifier = Modifier.weight(1.2f),
            style = io.element.android.compound.theme.ElementTheme.typography.fontBodyMdMedium
        )
        Text(
            text = "Type",
            modifier = Modifier.weight(1f),
            style = io.element.android.compound.theme.ElementTheme.typography.fontBodyMdMedium
        )
        Text(
            text = "Amount",
            modifier = Modifier.weight(1f),
            style = io.element.android.compound.theme.ElementTheme.typography.fontBodyMdMedium
        )
        Text(
            text = "Status",
            modifier = Modifier.weight(1f),
            style = io.element.android.compound.theme.ElementTheme.typography.fontBodyMdMedium
        )
    }
}

@Composable
fun TransactionRow(transaction: WalletTransaction) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Date Column
        Text(
            text = transaction.date.split("T").firstOrNull() ?: "",
            modifier = Modifier.weight(1.2f),
            style = io.element.android.compound.theme.ElementTheme.typography.fontBodySmRegular
        )

        // Type Column
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            val isCredit = transaction.type.contains("recharge", ignoreCase = true) || 
                          transaction.type.contains("credit", ignoreCase = true) ||
                          transaction.type.contains("CREDIT", ignoreCase = false)
            Icon(
                imageVector = if (isCredit) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = null,
                tint = if (isCredit) Color.Green else Color.Red,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = transaction.type.lowercase().replaceFirstChar { it.uppercase() },
                style = io.element.android.compound.theme.ElementTheme.typography.fontBodySmRegular,
                maxLines = 1
            )
        }

        // Amount Column
        Text(
            text = "${transaction.amount} cr",
            modifier = Modifier.weight(1f),
            style = io.element.android.compound.theme.ElementTheme.typography.fontBodySmRegular
        )

        // Status Column
        Text(
            text = transaction.status.lowercase().replaceFirstChar { it.uppercase() },
            modifier = Modifier.weight(1f),
            color = when (transaction.status.lowercase()) {
                "completed", "success", "paid" -> io.element.android.compound.theme.ElementTheme.colors.textSuccessPrimary
                "failed" -> io.element.android.compound.theme.ElementTheme.colors.textCriticalPrimary
                else -> io.element.android.compound.theme.ElementTheme.colors.textSecondary
            },
            style = io.element.android.compound.theme.ElementTheme.typography.fontBodySmMedium
        )
    }
}

private fun startPayment(activity: Activity, orderId: String, amount: Int, key: String) {
    val checkout = Checkout()
    checkout.setKeyID(key)
    try {
        val options = JSONObject()
        options.put("name", "Clariva")
        options.put("description", "Wallet Recharge")
        options.put("order_id", orderId)
        options.put("theme.color", "#3399cc")
        options.put("currency", "INR")
        options.put("amount", amount) // amount in paisa

        checkout.open(activity, options)
    } catch (e: Exception) {
        Timber.e(e, "Error in starting Razorpay Checkout")
    }
}
