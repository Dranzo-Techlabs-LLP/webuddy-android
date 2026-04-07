/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.bankdetails

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.network.wallet.BankDetails
import io.element.android.libraries.network.wallet.WalletService
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.launch

@Inject
class BankDetailsPresenter(
    private val walletService: WalletService,
    private val matrixClient: MatrixClient,
) : Presenter<BankDetailsState> {

    @Composable
    override fun present(): BankDetailsState {
        val coroutineScope = rememberCoroutineScope()
        val bankNameState = remember { mutableStateOf("") }
        val accountNumberState = remember { mutableStateOf("") }
        val ifscCodeState = remember { mutableStateOf("") }
        val accountHolderNameState = remember { mutableStateOf("") }
        val branchNameState = remember { mutableStateOf("") }
        val saveAction = remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }

        fun handleEvent(event: BankDetailsEvents) {
            when (event) {
                is BankDetailsEvents.SetBankName -> bankNameState.value = event.bankName
                is BankDetailsEvents.SetAccountNumber -> accountNumberState.value = event.accountNumber
                is BankDetailsEvents.SetIfscCode -> ifscCodeState.value = event.ifscCode
                is BankDetailsEvents.SetAccountHolderName -> accountHolderNameState.value = event.accountHolderName
                is BankDetailsEvents.SetBranchName -> branchNameState.value = event.branchName
                is BankDetailsEvents.Save -> coroutineScope.launch {
                    val bankDetails = BankDetails(
                        expertId = matrixClient.sessionId.value,
                        bankName = bankNameState.value,
                        accountNumber = accountNumberState.value,
                        ifscCode = ifscCodeState.value,
                        accountHolderName = accountHolderNameState.value,
                        branchName = branchNameState.value
                    )
                    saveAction.runCatchingUpdatingState {
                        walletService.saveBankDetails(bankDetails)
                    }
                }
                is BankDetailsEvents.ClearActionError -> saveAction.value = AsyncAction.Uninitialized
            }
        }

        return BankDetailsState(
            bankName = bankNameState.value,
            accountNumber = accountNumberState.value,
            ifscCode = ifscCodeState.value,
            accountHolderName = accountHolderNameState.value,
            branchName = branchNameState.value,
            saveAction = saveAction.value,
            eventSink = ::handleEvent
        )
    }
}
