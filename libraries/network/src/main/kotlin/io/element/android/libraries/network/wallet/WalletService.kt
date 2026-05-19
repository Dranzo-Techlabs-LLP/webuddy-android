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

    // Current logged-in user's role. null = not yet known/loaded; true = consultant; false = normal client.
    private val _isCurrentUserConsultant = MutableStateFlow<Boolean?>(null)
    val isCurrentUserConsultant: StateFlow<Boolean?> = _isCurrentUserConsultant.asStateFlow()

    // Pending role selection picked on the account-creation screen, applied when the wallet user is created
    // after the Matrix.org webview returns. Default = false (Normal User). Reset to default once consumed.
    private val _pendingIsConsultant = MutableStateFlow(false)
    val pendingIsConsultant: StateFlow<Boolean> = _pendingIsConsultant.asStateFlow()

    // Set of client Matrix IDs that currently have an open refund request awaiting this
    // consultant's decision. Powers the chat-list refund-pending badge so consultants
    // can spot affected rooms without opening each chat individually.
    private val _pendingRefundRequestClients = MutableStateFlow<Set<String>>(emptySet())
    val pendingRefundRequestClients: StateFlow<Set<String>> = _pendingRefundRequestClients.asStateFlow()

    fun setPendingIsConsultant(value: Boolean) {
        Timber.d("Pending isConsultant set to $value")
        _pendingIsConsultant.value = value
    }

    private val creditsCache = ConcurrentHashMap<String, Int>()
    // Per-user isConsultant cache populated as a side-effect of getMaxCredits. Used by the
    // chat-list factory to decide whether to render the "X credits" per-row rate: only a
    // consultant has a chat rate; for client-to-client rooms the row stays blank.
    private val isConsultantCache = ConcurrentHashMap<String, Boolean>()
    private val orderToTransactionMap = ConcurrentHashMap<String, String>()

    // Tracks the last user whose wallet was refreshed, so we can flush stale in-memory state
    // when the active session changes (logout + login, switch account, etc). Without this,
    // WalletService is @SingleIn(AppScope) and would leak the previous user's credits/role
    // into the new user's session until refreshBalance completes — visible as e.g. the home
    // top-bar momentarily showing the previous account's balance.
    private var lastRefreshedUserId: String? = null

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
     * [isConsultant] should be 0 for normal users (default) or 1 for consultants.
     */
    //@Suppress("unused", "SpellCheckingInspection")
    suspend fun createUser(userId: String, isConsultant: Int = 0): Result<WalletResponse> {
        return try {
            val request = WalletCreateRequest(
                name = userId,
                webuddyName = userId,
                isConsultant = isConsultant,
            )
            val response = walletApi.createUser(request)
            Timber.d("Wallet user created successfully for $userId with isConsultant=$isConsultant")

            // Reflect the chosen role locally so the UI knows immediately.
            _isCurrentUserConsultant.value = isConsultant == 1
            // Pending role has now been applied; reset it.
            _pendingIsConsultant.value = false

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
        // B6 fix: when the active user changes (account switch / logout+login), flush stale state
        // before fetching the new user's data. This avoids showing the previous user's credits
        // and role during the window between login and the new refreshBalance completing.
        val previousUserId = lastRefreshedUserId
        if (previousUserId != null && previousUserId != userId) {
            Timber.d("Active user changed ($previousUserId -> $userId); clearing wallet cache")
            _credits.value = null
            _maxCredits.value = null
            _originalMaxCredits.value = null
            _isCurrentUserConsultant.value = null
            _pendingRefundRequestClients.value = emptySet()
            creditsCache.clear()
            isConsultantCache.clear()
            orderToTransactionMap.clear()
        }
        lastRefreshedUserId = userId

        try {
            Timber.d("Refreshing balance for user: $userId")
            val sessionData = sessionStore.getSession(userId)
            val identifier = sessionData?.webuddyName ?: userId
            val response = walletApi.getWalletBalance(identifier)

            // B4 fix: only overwrite credits/maxCredits on a real parsed value. Previously this
            // path used `?: 0` / `?: 100` fallbacks that clobbered the last-known-good value
            // whenever the API returned a null field or an unparseable string.
            val parsedBalance = response.currentHold
                ?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt()
            if (parsedBalance != null) _credits.value = parsedBalance

            val parsedMaxCredits = response.maxCredits
                ?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt()
            if (parsedMaxCredits != null) {
                _maxCredits.value = parsedMaxCredits
                _originalMaxCredits.value = parsedMaxCredits
                creditsCache[userId] = parsedMaxCredits
            }

            response.isConsultant?.let { _isCurrentUserConsultant.value = it == 1 }
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh wallet balance for user $userId")
            // Only seed defaults if we have no value at all; never clobber a known-good value
            // from a previous successful refresh.
            if (_credits.value == null) _credits.value = 0
            if (_maxCredits.value == null) {
                _maxCredits.value = 100
                _originalMaxCredits.value = 100
            }
        }
    }

    /**
     * Fetch the wallet info for [userId] — which may be a different user than the locally
     * logged-in account. Populates [creditsCache] (max_credits) and [isConsultantCache]
     * (whether that user is a consultant) as side effects.
     *
     * Note: this MUST NOT touch [_maxCredits] / [_originalMaxCredits] — those state flows
     * track the LOCAL user's own settings and are owned by [refreshBalance] / [setMaxCredits] /
     * [updateMaxCredits]. The previous implementation wrote every queried recipient's
     * max_credits into the local state, which corrupted the consultant's own rate every time
     * the chat-list factory looked up a chat partner.
     */
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
            val maxCreditsValue = response.maxCredits
                ?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt()
            if (maxCreditsValue != null) {
                creditsCache[userId] = maxCreditsValue
            }
            response.isConsultant?.let { isConsultantCache[userId] = it == 1 }
            maxCreditsValue
        } catch (e: Exception) {
            Timber.e(e, "Failed to get max credits for user $userId. Error: ${e.message}")
            null
        }
    }

    /**
     * Returns [userId]'s chat rate (their max_credits) ONLY if they are a consultant.
     * For clients (or unknown role), returns null — clients don't have a rate, so the
     * chat-list row stays blank rather than showing a meaningless number.
     */
    suspend fun getRecipientChatRate(userId: String): Int? {
        // Make sure both caches are populated. getMaxCredits is a cache-first lookup.
        val maxCredits = getMaxCredits(userId)
        return if (isConsultantCache[userId] == true) maxCredits else null
    }

    fun setMaxCredits(maxCredits: Int) {
        _maxCredits.value = maxCredits
    }

    /**
     * Persist the current user's role (consultant vs. normal client) to the wallet API.
     * Returns success once the PATCH succeeds and the local cache is updated.
     */
    suspend fun setRole(userId: String, isConsultant: Boolean): Result<Unit> {
        return try {
            val sessionData = sessionStore.getSession(userId)
            val identifier = sessionData?.webuddyName ?: userId
            walletApi.updateWallet(identifier, WalletUpdateRequest(isConsultant = if (isConsultant) 1 else 0))
            _isCurrentUserConsultant.value = isConsultant
            Timber.d("Role set for $userId: isConsultant=$isConsultant")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to set role for $userId")
            Result.failure(e)
        }
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
        // Consultants don't pay other consultants — only normal users (clients) initiate holds.
        if (_isCurrentUserConsultant.value == true) {
            Timber.d("Skipping initiateHold: current user is a consultant")
            return Result.success(Unit)
        }
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

    suspend fun approveRefund(refundRequestId: String, pendingHoldId: String): Result<GenericRefundResponse> {
        return try {
            val response = walletApi.approveRefund(
                ApproveRefundRequest(refundRequestId = refundRequestId, pendingHoldId = pendingHoldId)
            )
            Timber.d("Refund approved: ${response.message}")
            Result.success(response)
        } catch (e: Exception) {
            Timber.e(e, "Failed to approve refund")
            Result.failure(e)
        }
    }

    /**
     * Refresh the set of clients with an open refund request awaiting this consultant.
     * No-op (and clears the set) if the current user is not a consultant — the chat-list
     * badge only renders for consultants. Safe to call frequently; one API round-trip.
     */
    suspend fun refreshPendingRefundRequests(consultantUserId: String) {
        if (_isCurrentUserConsultant.value != true) {
            _pendingRefundRequestClients.value = emptySet()
            return
        }
        try {
            val sessionData = sessionStore.getSession(consultantUserId)
            val identifier = sessionData?.webuddyName ?: consultantUserId
            val response = walletApi.pendingRefundsForConsultant(identifier)
            _pendingRefundRequestClients.value = response.clientIds.toSet()
            Timber.d("Pending refund requests for $consultantUserId: ${response.clientIds.size}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh pending refund requests for $consultantUserId")
            // On failure, leave the previous value untouched — better to show a stale badge
            // than to clear correct state because of one network blip.
        }
    }

    suspend fun rejectRefund(refundRequestId: String): Result<GenericRefundResponse> {
        return try {
            val response = walletApi.rejectRefund(RejectRefundRequest(refundRequestId = refundRequestId))
            Timber.d("Refund rejected: ${response.message}")
            Result.success(response)
        } catch (e: Exception) {
            Timber.e(e, "Failed to reject refund")
            Result.failure(e)
        }
    }

    /**
     * Try to atomically claim the right to post the in-room Matrix push-notification
     * message for an auto-approved refund.
     *
     * Returns `true` if THIS device won the claim and should send the message. Returns
     * `false` if another device already claimed it (do not send, the room is covered)
     * OR if the network call failed (do not send to avoid duplicates on retry).
     */
    suspend fun claimAutoApprovalNotification(refundRequestId: Int, callerUserId: String): Boolean {
        return try {
            val sessionData = sessionStore.getSession(callerUserId)
            val identifier = sessionData?.webuddyName ?: callerUserId
            val response = walletApi.claimAutoApprovalNotification(
                refundRequestId = refundRequestId.toString(),
                request = ClaimAutoApprovalRequest(userId = identifier),
            )
            Timber.d("Auto-approval claim for $refundRequestId by $callerUserId: claimed=${response.claimed}")
            response.claimed
        } catch (e: Exception) {
            Timber.e(e, "Failed to claim auto-approval notification for $refundRequestId; skipping send")
            false
        }
    }
}

sealed class WalletPaymentResult {
    data class Success(val orderId: String?, val paymentId: String?, val signature: String?) : WalletPaymentResult()
    data class Error(val code: Int, val message: String?, val orderId: String?) : WalletPaymentResult()
}
