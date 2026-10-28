/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.clarivaauth

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** The fields of the optional consultant setup on the sign-up form. */
enum class ConsultantSetupField {
    MaxCredits,
    AccountHolderName,
    BankName,
    AccountNumber,
    IfscCode,
    BranchName,
}

/**
 * Optional chat rate and payout bank account, shown on sign-up only once the
 * user says they are a consultant. Everything here can be left empty or
 * skipped; it can all be set later in Settings > Billing and payments.
 *
 * Values are kept exactly as typed (see SetUsername for why) and normalised by
 * the getters below.
 */
@Parcelize
data class ConsultantSetupForm(
    val isSkipped: Boolean = false,
    val maxCredits: String = "",
    val accountHolderName: String = "",
    val bankName: String = "",
    val accountNumber: String = "",
    val ifscCode: String = "",
    val branchName: String = "",
) : Parcelable {
    fun valueOf(field: ConsultantSetupField): String = when (field) {
        ConsultantSetupField.MaxCredits -> maxCredits
        ConsultantSetupField.AccountHolderName -> accountHolderName
        ConsultantSetupField.BankName -> bankName
        ConsultantSetupField.AccountNumber -> accountNumber
        ConsultantSetupField.IfscCode -> ifscCode
        ConsultantSetupField.BranchName -> branchName
    }

    fun with(field: ConsultantSetupField, value: String): ConsultantSetupForm = when (field) {
        ConsultantSetupField.MaxCredits -> copy(maxCredits = value)
        ConsultantSetupField.AccountHolderName -> copy(accountHolderName = value)
        ConsultantSetupField.BankName -> copy(bankName = value)
        ConsultantSetupField.AccountNumber -> copy(accountNumber = value)
        ConsultantSetupField.IfscCode -> copy(ifscCode = value)
        ConsultantSetupField.BranchName -> copy(branchName = value)
    }

    /** Null when left empty: the rate is then set later. */
    val maxCreditsValue: Int? get() = maxCredits.trim().toIntOrNull()?.takeIf { it in MIN_MAX_CREDITS..MAX_MAX_CREDITS }

    val normalizedIfsc: String get() = ifscCode.trim().uppercase()

    val maxCreditsError: String?
        get() = if (maxCredits.isNotBlank() && maxCreditsValue == null) {
            "Enter a whole number from $MIN_MAX_CREDITS to $MAX_MAX_CREDITS"
        } else {
            null
        }

    val accountNumberError: String?
        get() = if (accountNumber.isNotBlank() && !ACCOUNT_NUMBER_REGEX.matches(accountNumber.trim())) {
            "Account number must be 9 to 18 digits"
        } else {
            null
        }

    val ifscError: String?
        get() = if (ifscCode.isNotBlank() && !IFSC_REGEX.matches(normalizedIfsc)) {
            "IFSC code looks like ABCD0123456"
        } else {
            null
        }

    /** True once any bank field is filled: from then on all required ones are needed. */
    val hasBankDetails: Boolean
        get() = listOf(accountHolderName, bankName, accountNumber, ifscCode, branchName).any { it.isNotBlank() }

    private val isBankDetailsComplete: Boolean
        get() = accountHolderName.trim().length >= MIN_NAME_LENGTH &&
            bankName.trim().length >= MIN_NAME_LENGTH &&
            ACCOUNT_NUMBER_REGEX.matches(accountNumber.trim()) &&
            IFSC_REGEX.matches(normalizedIfsc)

    /** Shown under the bank section while it is only partly filled. */
    val isBankDetailsIncomplete: Boolean get() = hasBankDetails && !isBankDetailsComplete

    /** Skipped, empty, or fully valid: anything else blocks "Create account". */
    val isValid: Boolean
        get() = isSkipped || (maxCreditsError == null && (!hasBankDetails || isBankDetailsComplete))

    /** Something will actually be saved after the account is created. */
    val hasAnythingToSave: Boolean get() = !isSkipped && (maxCreditsValue != null || hasBankDetails)

    companion object {
        const val MIN_MAX_CREDITS = 1
        const val MAX_MAX_CREDITS = 100_000
        const val MAX_CREDITS_MAX_DIGITS = 6
        const val ACCOUNT_NUMBER_MAX_DIGITS = 18
        const val IFSC_LENGTH = 11
        const val MAX_TEXT_LENGTH = 120
        private const val MIN_NAME_LENGTH = 2

        // Kept in sync with the API's AddMyBankDetailDto.
        val ACCOUNT_NUMBER_REGEX = Regex("^\\d{9,18}$")
        val IFSC_REGEX = Regex("^[A-Z]{4}0[A-Z0-9]{6}$")
    }
}
