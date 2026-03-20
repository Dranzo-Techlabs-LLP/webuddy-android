/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.bankdetails

sealed interface BankDetailsEvents {
    data class SetBankName(val bankName: String) : BankDetailsEvents
    data class SetAccountNumber(val accountNumber: String) : BankDetailsEvents
    data class SetIfscCode(val ifscCode: String) : BankDetailsEvents
    data class SetAccountHolderName(val accountHolderName: String) : BankDetailsEvents
    data class SetBranchName(val branchName: String) : BankDetailsEvents
    object Save : BankDetailsEvents
    object ClearActionError : BankDetailsEvents
}
