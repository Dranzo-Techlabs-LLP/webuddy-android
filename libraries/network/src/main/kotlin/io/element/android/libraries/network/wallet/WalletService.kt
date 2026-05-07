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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private val _originalMaxCredits = MutableStateFlow<Int?>(null)
    val originalMaxCredits: StateFlow<Int?> = _originalMaxCredits.asStateFlow()

    private val creditsCache = ConcurrentHashMap<String, Int>()
    private val orderToTransactionMap = ConcurrentHashMap<String, String>()

    private val _paymentResults = MutableSharedFlow<WalletPaymentResult>(extraBufferCapacity = 1)
    val paymentResults: SharedFlow<WalletPaymentResult> = _paymentResults.asSharedFlow()

    suspend fun emitPaymentSuccess(orderId: String?, paymentId: String?, signature: String?) {
        _paymentResults.emit(WalletPaymentResult.Success(orderId, paymentId, signature))
    }

    suspend fun emitPaymentError(code: Int, message: String?, orderId: String?) {
        _paymentResults.emit(WalletPaymentResult.Error(code, message, orderId))
    }

    fun getTransactionIdForOrder(orderId: String): String? {
        return orderToTransactionMap[orderId]
    }

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
            val sessionData = sessionStore.getSession(userId)
            val identifier = sessionData?.webuddyName ?: userId
            val response = walletApi.getWalletBalance(identifier)
            
            val balance = response.currentHold?.let { 
                it.jsonPrimitive.content.toDoubleOrNull()?.toInt() 
            } ?: 0
            _credits.value = balance

            val maxCreditsValue = response.maxCredits?.let { 
                it.jsonPrimitive.content.toDoubleOrNull()?.toInt() 
            } ?: 100
            _maxCredits.value = maxCreditsValue
            _originalMaxCredits.value = maxCreditsValue

            creditsCache[userId] = maxCreditsValue
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh wallet balance for user $userId")
            if (_credits.value == null) _credits.value = 0
            if (_maxCredits.value == null) {
                _maxCredits.value = 100
                _originalMaxCredits.value = 100
            }
        }
    }

    suspend fun getMaxCredits(userId: String): Int? {
        creditsCache[userId]?.let { 
            Timber.d("Returning cached credits for $userId: $it")
            return it 
        }
        return try {
            Timber.d("Fetching max credits from API for user: $userId")
            val sessionData = sessionStore.getSession(userId)
            val identifier = sessionData?.webuddyName ?: userId
            val response = walletApi.getWalletBalance(identifier)
            val maxCreditsValue = response.maxCredits?.let { 
                it.jsonPrimitive.content.toDoubleOrNull()?.toInt() 
            }
            if (maxCreditsValue != null) {
                creditsCache[userId] = maxCreditsValue
                _maxCredits.value = maxCreditsValue
                _originalMaxCredits.value = maxCreditsValue
            }
            maxCreditsValue
        } catch (e: Exception) {
            Timber.e(e, "Failed to get max credits for user $userId. Error: ${e.message}")
            null
        }
    }

    fun setMaxCredits(maxCredits: Int) {
        _maxCredits.value = maxCredits
    }

    suspend fun updateMaxCredits(userId: String, maxCredits: Int) {
        try {
            val sessionData = sessionStore.getSession(userId)
            val identifier = sessionData?.webuddyName ?: userId
            walletApi.updateWallet(identifier, WalletUpdateRequest(maxCredits = maxCredits))
            _maxCredits.value = maxCredits
            _originalMaxCredits.value = maxCredits
            creditsCache[userId] = maxCredits
        } catch (e: Exception) {
            Timber.e(e, "Failed to update max credits for user $userId")
            throw e
        }
    }

    suspend fun saveBankDetails(bankDetails: BankDetails) {
        try {
            walletApi.postBankDetails(bankDetails)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save bank details")
            throw e
        }
    }

    suspend fun createOrder(userId: String, amount: Double): Result<CreateOrderResponse> {
        return try {
            val sessionData = sessionStore.getSession(userId)
            val identifier = sessionData?.webuddyName ?: userId
            val rechargeRequest = RechargeWalletRequest(
                userId = identifier,
                amount = amount
            )
            val response = walletApi.createOrder(rechargeRequest)
            Timber.d("Order created successfully for $userId: ${response.orderId}")
            
            // Store mapping
            orderToTransactionMap[response.orderId] = response.transactionId
            
            Result.success(response)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create order for $userId")
            Result.failure(e)
        }
    }

    suspend fun verifyPayment(
        userId: String,
        transactionId: String,
        razorpayOrderId: String,
        razorpayPaymentId: String,
        razorpaySignature: String
    ): Result<VerifyPaymentResponse> {
        return try {
            val sessionData = sessionStore.getSession(userId)
            val identifier = sessionData?.webuddyName ?: userId
            val request = VerifyPaymentRequest(
                userId = identifier,
                transactionId = transactionId,
                razorpayOrderId = razorpayOrderId,
                razorpayPaymentId = razorpayPaymentId,
                razorpaySignature = razorpaySignature
            )
            val response = walletApi.verifyPayment(request)
            Timber.d("Payment verified successfully: ${response.success}")
            // Refresh balance after successful verification
            if (response.success) {
                refreshBalance(userId)
            }
            Result.success(response)
        } catch (e: Exception) {
            Timber.e(e, "Failed to verify payment for $userId")
            Result.failure(e)
        }
    }

    suspend fun getTransactionHistory(userId: String, page: Int = 1, pageSize: Int = 10): Result<TransactionHistoryResponse> {
        return try {
            val sessionData = sessionStore.getSession(userId)
            val identifier = sessionData?.webuddyName ?: userId
            val response = walletApi.getTransactionHistory(identifier, page, pageSize)
            Result.success(response)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get transaction history for $userId")
            Result.failure(e)
        }
    }

    suspend fun initiateHold(clientId: String, consultantId: String): Result<Unit> {
        return try {
            val clientData = sessionStore.getSession(clientId)
            val consultantData = sessionStore.getSession(consultantId)
            
            val request = InitiateHoldRequest(
                clientId = clientData?.webuddyName ?: clientId,
                consultantId = consultantData?.webuddyName ?: consultantId
            )
            walletApi.initiateHold(request)
            Timber.d("Hold initiated successfully between $clientId and $consultantId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initiate hold between $clientId and $consultantId")
            Result.failure(e)
        }
    }

    suspend fun checkHoldExists(clientId: String, consultantId: String): Boolean {
        return try {
            val clientData = sessionStore.getSession(clientId)
            val consultantData = sessionStore.getSession(consultantId)
            
            val response = walletApi.checkHoldExists(
                clientId = clientData?.webuddyName ?: clientId,
                consultantId = consultantData?.webuddyName ?: consultantId
            )
            response.exists
        } catch (e: Exception) {
            Timber.e(e, "Failed to check hold existence between $clientId and $consultantId")
            false
        }
    }
    suspend fun getPendingHoldStatus(clientId: String, consultantId: String): PendingHoldStatusResponse {
        val clientData = sessionStore.getSession(clientId)
        val consultantData = sessionStore.getSession(consultantId)
        
        return walletApi.getPendingHoldStatus(
            clientId = clientData?.webuddyName ?: clientId,
            consultantId = consultantData?.webuddyName ?: consultantId
        )
    }

    suspend fun
        requestRefund(clientId: String, consultantId: String, pendingHoldId: String): Result<RefundResponse> {
        return try {
            val clientData = sessionStore.getSession(clientId)
            val consultantData = sessionStore.getSession(consultantId)
            
            val request = RefundRequest(
                clientId = clientData?.webuddyName ?: clientId,
                consultantId = consultantData?.webuddyName ?: consultantId,
                pendingHoldId = pendingHoldId
            )
            val response = walletApi.requestRefund(request)
            Timber.d("Refund requested successfully: ${response.message}")
            Result.success(response)
        } catch (e: Exception) {
            Timber.e(e, "Failed to request refund")
            Result.failure(e)
        }
    }
}

sealed class WalletPaymentResult {
    data class Success(val orderId: String?, val paymentId: String?, val signature: String?) : WalletPaymentResult()
    data class Error(val code: Int, val message: String?, val orderId: String?) : WalletPaymentResult()
}
