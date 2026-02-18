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
import java.util.concurrent.ConcurrentHashMap

@SingleIn(AppScope::class)
class WalletService @Inject constructor(
    private val walletApi: WalletApi
) {
    private val _credits = MutableStateFlow<Int?>(null)
    val credits: StateFlow<Int?> = _credits.asStateFlow()

    private val creditsCache = ConcurrentHashMap<String, Int?>()

    suspend fun refreshBalance(userId: String) {
        try {
            // userId here is likely the localpart (e.g. "shinky777") based on how it's called in HomePresenter
            // If the top wallet also needs full ID, we should change it in HomePresenter too.
            Timber.d("Refreshing balance for user: $userId")
            val response = walletApi.getWalletBalance(userId)
            // current_hold is for the top wallet
            val balance = response.currentHold.toDoubleOrNull()?.toInt() ?: 0
            _credits.value = balance
            // Also cache max_credits for this user
            response.maxCredits?.toDoubleOrNull()?.toInt()?.let {
                creditsCache[userId] = it
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh wallet balance for user $userId")
        }
    }

    suspend fun getMaxCredits(userId: String): Int? {
        creditsCache[userId]?.let { 
            Timber.d("Returning cached credits for $userId: $it")
            return it 
        }
        return try {
            Timber.d("Fetching max credits from API for user: $userId")
            val response = walletApi.getWalletBalance(userId)
            Timber.d("API Response for $userId: $response")
            val maxCredits = response.maxCredits?.toDoubleOrNull()?.toInt()
            if (maxCredits != null) {
                creditsCache[userId] = maxCredits
            }
            maxCredits
        } catch (e: Exception) {
            Timber.e(e, "Failed to get max credits for user $userId. Error: ${e.message}")
            null
        }
    }
}
