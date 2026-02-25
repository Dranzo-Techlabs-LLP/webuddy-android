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
data class WalletResponse(
    @SerialName("current_hold") val currentHold: String,
    @SerialName("max_credits") val maxCredits: String? = null,
    @SerialName("Webuddy_name") val webuddyName: String? = null,
)

@Serializable
data class WalletUpdateRequest(
    @SerialName("max_credits") val maxCredits: Int
)
