/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.network.wallet

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BankDetails(
    @SerialName("expertId") val expertId: String,
    @SerialName("bankName") val bankName: String,
    @SerialName("accountNumber") val accountNumber: String,
    @SerialName("ifscCode") val ifscCode: String,
    @SerialName("accountHolderName") val accountHolderName: String,
    @SerialName("branchName") val branchName: String,
)
