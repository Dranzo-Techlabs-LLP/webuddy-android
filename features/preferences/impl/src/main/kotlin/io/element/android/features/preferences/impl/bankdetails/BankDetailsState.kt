/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.bankdetails

import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.network.wallet.BankAccountRules
import io.element.android.libraries.network.wallet.IfscStatus
import io.element.android.libraries.network.wallet.SavedBankAccount

data class BankDetailsState(
    /** The account payouts currently go to; Success(null) when none is saved yet. */
    val savedAccount: AsyncData<SavedBankAccount?>,
    val bankName: String,
    val accountNumber: String,
    val ifscCode: String,
    val accountHolderName: String,
    val branchName: String,
    /** Live check of [ifscCode] against the IFSC directory. */
    val ifscStatus: IfscStatus,
    val saveAction: AsyncAction<Unit>,
    val eventSink: (BankDetailsEvents) -> Unit
) {
    // Errors only once something is typed, so an empty form does not nag.
    val accountNumberError: String?
        get() = if (accountNumber.isNotBlank() && !BankAccountRules.isValidAccountNumber(accountNumber)) {
            "Account number must be 9 to 18 digits"
        } else {
            null
        }

    val ifscError: String?
        get() = if (ifscCode.isNotBlank() && !BankAccountRules.isValidIfsc(ifscCode)) {
            "IFSC code looks like ABCD0123456"
        } else {
            null
        }

    /** Branch is optional; everything else must be valid. */
    val canSave: Boolean
        get() = !saveAction.isLoading() &&
            BankAccountRules.isValidName(accountHolderName) &&
            BankAccountRules.isValidName(bankName) &&
            BankAccountRules.isValidAccountNumber(accountNumber) &&
            BankAccountRules.isValidIfsc(ifscCode) &&
            // An IFSC with no branch would only surface as a failed payout. While checking, wait;
            // if the directory is unreachable, allow it - the API checks again on save.
            !ifscStatus.blocksSave
}
