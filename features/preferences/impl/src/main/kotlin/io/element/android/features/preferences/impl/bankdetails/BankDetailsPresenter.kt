/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.bankdetails

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.network.wallet.BankAccountRules
import io.element.android.libraries.network.wallet.ClarivaBankDetailsRequest
import io.element.android.libraries.network.wallet.IfscBranch
import io.element.android.libraries.network.wallet.IfscLookup
import io.element.android.libraries.network.wallet.IfscStatus
import io.element.android.libraries.network.wallet.SavedBankAccount
import io.element.android.libraries.network.wallet.WalletService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Inject
class BankDetailsPresenter(
    private val walletService: WalletService,
    private val matrixClient: MatrixClient,
    private val ifscLookup: IfscLookup,
) : Presenter<BankDetailsState> {

    @Composable
    override fun present(): BankDetailsState {
        val coroutineScope = rememberCoroutineScope()
        val userId = matrixClient.sessionId.value
        var bankName by rememberSaveable { mutableStateOf("") }
        var accountNumber by rememberSaveable { mutableStateOf("") }
        var ifscCode by rememberSaveable { mutableStateOf("") }
        var accountHolderName by rememberSaveable { mutableStateOf("") }
        var branchName by rememberSaveable { mutableStateOf("") }
        var savedAccount by remember { mutableStateOf<AsyncData<SavedBankAccount?>>(AsyncData.Loading()) }
        var loadAttempt by remember { mutableIntStateOf(0) }
        val saveAction = remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }
        var ifscStatus by remember { mutableStateOf<IfscStatus>(IfscStatus.None) }
        // What the last lookup filled in, so a new IFSC can replace it but never a value the user typed.
        var autofilled by remember { mutableStateOf<IfscBranch?>(null) }

        val normalizedIfsc = ifscCode.trim().uppercase()
        LaunchedEffect(normalizedIfsc) {
            if (!BankAccountRules.isValidIfsc(normalizedIfsc)) {
                ifscStatus = IfscStatus.None
                return@LaunchedEffect
            }
            ifscStatus = IfscStatus.Checking
            delay(IFSC_LOOKUP_DEBOUNCE_MS)
            val status = ifscLookup.lookup(normalizedIfsc)
            ifscStatus = status
            if (status is IfscStatus.Found) {
                val found = status.branch
                if (bankName.isBlank() || bankName == autofilled?.bank) bankName = found.bank
                if (branchName.isBlank() || branchName == autofilled?.branch) branchName = found.branch
                autofilled = found
            }
        }

        LaunchedEffect(loadAttempt) {
            savedAccount = AsyncData.Loading()
            savedAccount = walletService.getMyBankDetails(userId).fold(
                onSuccess = { AsyncData.Success(it) },
                onFailure = { AsyncData.Failure(it) },
            )
        }

        fun handleEvent(event: BankDetailsEvents) {
            when (event) {
                is BankDetailsEvents.SetBankName -> bankName = event.bankName
                is BankDetailsEvents.SetAccountNumber -> accountNumber = event.accountNumber
                is BankDetailsEvents.SetIfscCode -> ifscCode = event.ifscCode
                is BankDetailsEvents.SetAccountHolderName -> accountHolderName = event.accountHolderName
                is BankDetailsEvents.SetBranchName -> branchName = event.branchName
                is BankDetailsEvents.Save -> coroutineScope.launch {
                    // Normalised here, not while typing: rewriting the text mid-keystroke desyncs the IME.
                    val request = ClarivaBankDetailsRequest(
                        accountHolderName = accountHolderName.trim(),
                        bankName = bankName.trim(),
                        accountNumber = accountNumber.trim(),
                        ifscCode = ifscCode.trim().uppercase(),
                        branchName = branchName.trim().takeIf { it.isNotEmpty() },
                    )
                    saveAction.runCatchingUpdatingState {
                        val saved = walletService.saveMyBankDetails(userId, request).getOrThrow()
                        savedAccount = AsyncData.Success(saved)
                    }
                }
                is BankDetailsEvents.ClearActionError -> saveAction.value = AsyncAction.Uninitialized
                is BankDetailsEvents.RetryLoad -> loadAttempt++
            }
        }

        return BankDetailsState(
            savedAccount = savedAccount,
            bankName = bankName,
            accountNumber = accountNumber,
            ifscCode = ifscCode,
            accountHolderName = accountHolderName,
            branchName = branchName,
            ifscStatus = ifscStatus,
            saveAction = saveAction.value,
            eventSink = ::handleEvent
        )
    }
}

/** Long enough that pasting or fast typing triggers one lookup, not one per key. */
private const val IFSC_LOOKUP_DEBOUNCE_MS = 300L
