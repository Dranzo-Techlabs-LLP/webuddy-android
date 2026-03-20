/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.bankdetails

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.utils.snackbar.LocalSnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarHost
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.designsystem.utils.snackbar.collectSnackbarMessageAsState
import io.element.android.libraries.designsystem.utils.snackbar.rememberSnackbarHostState
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun BankDetailsView(
    state: BankDetailsState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarDispatcher = LocalSnackbarDispatcher.current
    val snackbarMessage by snackbarDispatcher.collectSnackbarMessageAsState()
    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = snackbarMessage)

    LaunchedEffect(state.saveAction) {
        when (state.saveAction) {
            is AsyncAction.Success -> {
                snackbarDispatcher.post(SnackbarMessage(CommonStrings.common_saved_changes))
                onBackClick()
            }
            is AsyncAction.Failure -> {
                snackbarDispatcher.post(SnackbarMessage(CommonStrings.common_error))
                state.eventSink(BankDetailsEvents.ClearActionError)
            }
            else -> Unit
        }
    }

    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = "Bank Details",
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            TextField(
                value = state.bankName,
                onValueChange = { state.eventSink(BankDetailsEvents.SetBankName(it)) },
                label = "Bank Name",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.saveAction.isLoading()
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = state.accountNumber,
                onValueChange = { state.eventSink(BankDetailsEvents.SetAccountNumber(it)) },
                label = "Account Number",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.saveAction.isLoading()
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = state.ifscCode,
                onValueChange = { state.eventSink(BankDetailsEvents.SetIfscCode(it)) },
                label = "IFSC Code",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.saveAction.isLoading()
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = state.accountHolderName,
                onValueChange = { state.eventSink(BankDetailsEvents.SetAccountHolderName(it)) },
                label = "Account Holder Name",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.saveAction.isLoading()
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = state.branchName,
                onValueChange = { state.eventSink(BankDetailsEvents.SetBranchName(it)) },
                label = "Branch Name",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.saveAction.isLoading()
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                text = "Save Details",
                onClick = { state.eventSink(BankDetailsEvents.Save) },
                modifier = Modifier.fillMaxWidth(),
                showProgress = state.saveAction.isLoading(),
                enabled = !state.saveAction.isLoading() && 
                        state.bankName.isNotBlank() && 
                        state.accountNumber.isNotBlank() && 
                        state.ifscCode.isNotBlank() && 
                        state.accountHolderName.isNotBlank() && 
                        state.branchName.isNotBlank()
            )
        }
    }
}
