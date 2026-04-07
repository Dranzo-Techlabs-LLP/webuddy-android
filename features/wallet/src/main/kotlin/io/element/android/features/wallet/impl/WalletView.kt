/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.wallet.impl

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.razorpay.Checkout
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Text
import org.json.JSONObject
import timber.log.Timber

@Composable
fun WalletView(
    state: WalletState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current

    // Handle payment flow when rechargeAction is Success
    LaunchedEffect(state.rechargeAction) {
        val action = state.rechargeAction
        if (action is AsyncAction.Success && activity != null) {
            val order = action.data
            startPayment(activity, order.orderId, order.amountInPaise, order.keyId)
            // Clear the action so it doesn't trigger again on recomposition
            state.eventSink(WalletEvents.ClearAction)
        }
    }

    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = "Wallet"
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Current Balance",
                style = io.element.android.compound.theme.ElementTheme.typography.fontBodyLgRegular
            )
            Text(
                text = "${state.credits ?: 0} credits",
                style = io.element.android.compound.theme.ElementTheme.typography.fontHeadingLgBold
            )
            Spacer(modifier = Modifier.height(32.dp))
            
            if (state.rechargeAction.isLoading()) {
                Text(text = "Preparing recharge...")
            } else {
                Button(
                    text = "Recharge Credits",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { state.eventSink(WalletEvents.Recharge(100)) }
                )
            }

            if (state.paymentVerificationAction is AsyncAction.Success) {
                Text(
                    text = "Recharge successful!",
                    color = io.element.android.compound.theme.ElementTheme.colors.textSuccessPrimary,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

private fun startPayment(activity: Activity, orderId: String, amount: Int, key: String) {
    val checkout = Checkout()
    checkout.setKeyID(key)
    try {
        val options = JSONObject()
        options.put("name", "Webuddy")
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
