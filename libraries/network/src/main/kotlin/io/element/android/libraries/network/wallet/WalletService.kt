/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.network.wallet

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.AppScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

@SingleIn(AppScope::class)
class WalletService @Inject constructor(
    private val walletApi: WalletApi
) {
    private val _credits = MutableStateFlow<Int?>(null)
    val credits: StateFlow<Int?> = _credits.asStateFlow()

    suspend fun refreshBalance(userId: String) {
        try {
            val response = walletApi.getWalletBalance(userId)
            // Convert "200.00" string to Double, then Int
            val balance = response.currentHold.toDoubleOrNull()?.toInt() ?: 0
            _credits.value = balance
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh wallet balance for user $userId")
        }
    }
}
