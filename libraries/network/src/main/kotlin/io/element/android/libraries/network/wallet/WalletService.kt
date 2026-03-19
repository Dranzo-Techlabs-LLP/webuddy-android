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
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

@SingleIn(AppScope::class)
class WalletService @Inject constructor(
    private val walletApi: WalletApi,
    private val sessionStore: SessionStore,
) {
    private val _credits = MutableStateFlow<Int?>(null)
    val credits: StateFlow<Int?> = _credits.asStateFlow()

    private val _maxCredits = MutableStateFlow<Int?>(null)
    val maxCredits: StateFlow<Int?> = _maxCredits.asStateFlow()

    private val _originalMaxCredits = MutableStateFlow(0)
    val originalMaxCredits: StateFlow<Int> = _originalMaxCredits.asStateFlow()

    private val creditsCache = ConcurrentHashMap<String, Int>()

    /**
     * Create a wallet user for the given Matrix userId.
     */
    //@Suppress("unused", "SpellCheckingInspection")
    suspend fun createUser(userId: String): Result<WalletResponse> {
        return try {
            val request = WalletCreateRequest(
                name = userId,
                webuddyName = userId,
            )
            val response = walletApi.createUser(request)
            Timber.d("Wallet user created successfully for $userId")
            
            // On success, update local database
            response.webuddyName?.let { webuddyName ->
                sessionStore.updateWebuddyName(userId, webuddyName)
            }
            
            Result.success(response)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create wallet user for $userId")
            Result.failure(e)
        }
    }

    suspend fun refreshBalance(userId: String) {
        try {
            Timber.d("Refreshing balance for user: $userId")
            val response = walletApi.getWalletBalance(userId)
            
            val balance = response.currentHold?.let { 
                it.jsonPrimitive.content.toDoubleOrNull()?.toInt() 
            } ?: 0
            _credits.value = balance

            val maxCreditsValue = response.maxCredits?.let { 
                it.jsonPrimitive.content.toDoubleOrNull()?.toInt() 
            } ?: 0
            _maxCredits.value = maxCreditsValue
            _originalMaxCredits.value = maxCreditsValue

            response.webuddyName?.let {
                creditsCache[userId] = maxCreditsValue
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh wallet balance for user $userId")
        }
    }

    fun setMaxCredits(amount: Int) {
        _maxCredits.value = amount
    }

    suspend fun updateMaxCredits(userId: String, amount: Int) {
        try {
            walletApi.updateWallet(userId, WalletUpdateRequest(maxCredits = amount))
            _originalMaxCredits.value = amount
        } catch (e: Exception) {
            Timber.e(e, "Failed to update max credits for user $userId")
            throw e
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
            val maxCreditsValue = response.maxCredits?.let { 
                it.jsonPrimitive.content.toDoubleOrNull()?.toInt() 
            }
            if (maxCreditsValue != null) {
                creditsCache[userId] = maxCreditsValue
            }
            maxCreditsValue
        } catch (e: Exception) {
            Timber.e(e, "Failed to get max credits for user $userId. Error: ${e.message}")
            null
        }
    }
}
