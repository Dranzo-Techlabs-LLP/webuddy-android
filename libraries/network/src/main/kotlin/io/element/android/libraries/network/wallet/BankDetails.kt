/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.network.wallet

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Format rules for a payout bank account, shared by consultant sign-up and
 * Settings > Bank Details. Kept in sync with the API's AddMyBankDetailDto.
 */
object BankAccountRules {
    const val ACCOUNT_NUMBER_MAX_DIGITS = 18
    const val IFSC_LENGTH = 11
    const val MAX_TEXT_LENGTH = 120
    const val MIN_NAME_LENGTH = 2

    val ACCOUNT_NUMBER_REGEX = Regex("^\\d{9,18}$")
    val IFSC_REGEX = Regex("^[A-Z]{4}0[A-Z0-9]{6}$")

    fun isValidAccountNumber(value: String) = ACCOUNT_NUMBER_REGEX.matches(value.trim())
    fun isValidIfsc(value: String) = IFSC_REGEX.matches(value.trim().uppercase())
    fun isValidName(value: String) = value.trim().length >= MIN_NAME_LENGTH
}

/** The saved payout account as the API returns it: never the full account number. */
@Serializable
data class SavedBankAccount(
    @SerialName("id") val id: String? = null,
    @SerialName("bankName") val bankName: String,
    @SerialName("accountNumberLast4") val accountNumberLast4: String,
    @SerialName("ifscCode") val ifscCode: String,
    @SerialName("accountHolderName") val accountHolderName: String,
    @SerialName("branchName") val branchName: String? = null,
)

@Serializable
data class MyBankAccountResponse(
    // Null until a payout account has been saved.
    @SerialName("bankDetail") val bankDetail: SavedBankAccount? = null,
)

/**
 * The wallet API refused a request. [message] is the API's own explanation when it gave one
 * (e.g. "No bank branch found for IFSC ..."), which is fit to show the user for a 4xx.
 */
class WalletApiException(
    val code: Int,
    private val serverMessage: String?,
    cause: Throwable? = null,
) : Exception(serverMessage ?: "Wallet request failed (HTTP $code)", cause) {
    /** The API's message for a client error, or null when there is nothing fit to show. */
    val userMessage: String? get() = serverMessage?.takeIf { code in 400..499 }
}
