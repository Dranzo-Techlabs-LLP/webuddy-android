/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.network.wallet

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

@SingleIn(SessionScope::class)
class WalletService @Inject constructor(
    private val walletApi: WalletApi,
) {
    private val _credits = MutableStateFlow<Int?>(null)
    val credits: StateFlow<Int?> = _credits.asStateFlow()

    private val _maxCredits = MutableStateFlow<Int?>(null)
    val maxCredits: StateFlow<Int?> = _maxCredits.asStateFlow()

    private val _originalMaxCredits = MutableStateFlow<Int?>(null)
    val originalMaxCredits: StateFlow<Int?> = _originalMaxCredits.asStateFlow()

    suspend fun refreshBalance(userId: String) {
        Timber.d("Refreshing wallet balance for user: $userId")
        try {
            val response = walletApi.getWalletBalance(userId)
            Timber.d("Wallet response received: $response")
            
            // Convert currentHold to Int
            val balance = response.currentHold?.let { element ->
                try {
                    val primitive = element.jsonPrimitive
                    primitive.content.toDoubleOrNull()?.toInt()
                } catch (e: Exception) {
                    null
                }
            } ?: 0
            _credits.value = balance

            // Robust max_credits parsing
            val maxCreditsValue = response.maxCredits?.let { element ->
                try {
                    val primitive = element.jsonPrimitive
                    if (primitive.isString) {
                        primitive.content.toDoubleOrNull()?.toInt()
                    } else {
                        primitive.content.toIntOrNull() ?: primitive.content.toDoubleOrNull()?.toInt()
                    }
                } catch (e: Exception) {
                    null
                }
            } ?: 0

            _maxCredits.value = maxCreditsValue
            _originalMaxCredits.value = maxCreditsValue
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh wallet balance for user $userId")
            // Prevent permanent loading state on failure
            if (_credits.value == null) _credits.value = 0
            if (_maxCredits.value == null) {
                _maxCredits.value = 0
                _originalMaxCredits.value = 0
            }
        }
    }

    fun setMaxCredits(maxCredits: Int) {
        _maxCredits.value = maxCredits
    }

    suspend fun updateMaxCredits(userId: String, maxCredits: Int) {
        try {
            walletApi.updateWallet(userId, WalletUpdateRequest(maxCredits = maxCredits))
            _maxCredits.value = maxCredits
            _originalMaxCredits.value = maxCredits
        } catch (e: Exception) {
            Timber.e(e, "Failed to update max credits for user $userId")
            throw e
        }
    }
}
