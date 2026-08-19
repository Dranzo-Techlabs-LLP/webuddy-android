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
import retrofit2.HttpException
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

    /**
     * Ensure the wallet backend has a user record for [userId]. Self-healing entry point
     * used by wallet operations to recover from "User not found" 404s — e.g. when the
     * initial sign-up's createUser silently failed (network blip, race) and the user has
     * been operating without a wallet account ever since.
     *
     * Safe to call multiple times. If the local session already has a webuddyName, this
     * is a no-op. Otherwise calls [createUser]; any failure (including the backend already
     * having the user) is logged but not propagated — the caller will retry the original
     * operation regardless.
     */
    suspend fun ensureWalletUserExists(userId: String): Boolean {
        val session = sessionStore.getSession(userId)
        if (session?.webuddyName != null) {
            return true
        }
        val isConsultant = if (_pendingIsConsultant.value) 1 else 0
        Timber.w("ensureWalletUserExists: no local webuddyName for $userId; attempting createUser")
        return createUser(userId = userId, isConsultant = isConsultant).fold(
            onSuccess = { true },
            onFailure = { e ->
                Timber.w(e, "ensureWalletUserExists: createUser failed for $userId (may already exist on backend)")
                false
            }
        )
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
            applyBalanceResponse(userId, fetchWalletBalance(userId))
        } catch (e: HttpException) {
            if (e.code() == 404) {
                // Same self-heal as createOrder/getTransactionHistory: missing wallet record
                // on the backend. Without recovery here, the wallet screen sticks on default
                // balance forever and recharge would also 404 until something else triggers
                // ensureWalletUserExists.
                Timber.w("refreshBalance: 404 'User not found' for $userId — auto-creating wallet user and retrying once")
                ensureWalletUserExists(userId)
                try {
                    applyBalanceResponse(userId, fetchWalletBalance(userId))
                } catch (retryErr: Exception) {
                    Timber.e(retryErr, "Refresh balance retry failed for $userId after auto-create")
                    seedDefaultBalanceIfEmpty()
                }
            } else {
                Timber.e(e, "Failed to refresh wallet balance for user $userId")
                seedDefaultBalanceIfEmpty()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh wallet balance for user $userId")
            seedDefaultBalanceIfEmpty()
        }
    }

    private suspend fun fetchWalletBalance(userId: String): WalletResponse {
        val sessionData = sessionStore.getSession(userId)
        val identifier = sessionData?.webuddyName ?: userId
        return walletApi.getWalletBalance(identifier)
    }

    private fun applyBalanceResponse(userId: String, response: WalletResponse) {
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
    }

    private fun seedDefaultBalanceIfEmpty() {
        // Only seed defaults if we have no value at all; never clobber a known-good value
        // from a previous successful refresh.
        if (_credits.value == null) _credits.value = 0
        if (_maxCredits.value == null) {
            _maxCredits.value = 100
            _originalMaxCredits.value = 100
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
            Result.success(callCreateOrder(userId, amount))
        } catch (e: HttpException) {
            // Self-heal "User not found" 404s. Some users (those whose initial sign-up
            // createUser was silently swallowed by a network blip / race) never had a wallet
            // record created on the backend, so every recharge hits 404. Auto-create the
            // wallet user once and retry the recharge transparently.
            if (e.code() == 404) {
                Timber.w("createOrder: 404 'User not found' for $userId — auto-creating wallet user and retrying once")
                ensureWalletUserExists(userId)
                try {
                    Result.success(callCreateOrder(userId, amount))
                } catch (retryErr: Exception) {
                    Timber.e(retryErr, "Recharge retry failed for $userId after auto-create")
                    Result.failure(retryErr)
                }
            } else {
                Timber.e(e, "Failed to create order for $userId")
                Result.failure(e)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to create order for $userId")
            Result.failure(e)
        }
    }

    private suspend fun callCreateOrder(userId: String, amount: Double): CreateOrderResponse {
        val sessionData = sessionStore.getSession(userId)
        val identifier = sessionData?.webuddyName ?: userId
        val rechargeRequest = RechargeWalletRequest(
            userId = identifier,
            amount = amount
        )
        val response = walletApi.createOrder(rechargeRequest)
        Timber.d("Order created successfully for $userId: ${response.orderId}")
        orderToTransactionMap[response.orderId] = response.transactionId
        return response
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
            Result.success(callGetTransactionHistory(userId, page, pageSize))
        } catch (e: HttpException) {
            if (e.code() == 404) {
                // Same self-heal as createOrder: a missing wallet record on the backend.
                // Without this, the wallet screen would permanently show "No transactions found"
                // even after the user has a balance, because we can never page-load history.
                Timber.w("getTransactionHistory: 404 'User not found' for $userId — auto-creating wallet user and retrying once")
                ensureWalletUserExists(userId)
                try {
                    Result.success(callGetTransactionHistory(userId, page, pageSize))
                } catch (retryErr: Exception) {
                    Timber.e(retryErr, "Transaction history retry failed for $userId after auto-create")
                    Result.failure(retryErr)
                }
            } else {
                Timber.e(e, "Failed to get transaction history for $userId")
                Result.failure(e)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get transaction history for $userId")
            Result.failure(e)
        }
    }

    private suspend fun callGetTransactionHistory(
        userId: String,
        page: Int,
        pageSize: Int,
    ): TransactionHistoryResponse {
        val sessionData = sessionStore.getSession(userId)
        val identifier = sessionData?.webuddyName ?: userId
        return walletApi.getTransactionHistory(identifier, page, pageSize)
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

    /**
     * Record the caller's voice/video choice for the call about to start in [roomId]. Fire-safe:
     * a failure only means the receiver falls back to their default labeling — never block a call
     * on this write.
     */
    suspend fun setRoomCallType(roomId: String, isVideo: Boolean, callerId: String? = null) {
        try {
            walletApi.setRoomCallType(
                SetCallTypeRequest(
                    roomId = roomId,
                    callType = if (isVideo) "video" else "voice",
                    callerId = callerId,
                )
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to record call type for room $roomId")
        }
    }

    /**
     * The media type of the latest call in [roomId]: "voice", "video", or null when unknown
     * (no record — e.g. the caller runs an older app — or a network error).
     */
    suspend fun getRoomCallType(roomId: String): String? {
        return try {
            walletApi.getRoomCallType(roomId).callType
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch call type for room $roomId")
            null
        }
    }

    /**
     * Whether an active hold exists between the pair. Returns null on failure (network/parse error)
     * so callers can distinguish "confirmed no hold" (false) from "unknown" (null). Returning false
     * on error previously made an ongoing paid session look ended — surfacing a wrong "session ended"
     * warning and a spurious chat restriction on a transient blip.
     */
    suspend fun checkHoldExists(clientId: String, consultantId: String): Boolean? {
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
            null
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

    /**
     * Approve a refund. [refundAmount] null => full refund (whole held amount back to the client).
     * A positive [refundAmount] does a PARTIAL refund: only that much returns to the client and the
     * consultant keeps the remainder (credited immediately server-side). Must be 0 < amount <= held.
     */
    suspend fun approveRefund(
        refundRequestId: String,
        pendingHoldId: String,
        refundAmount: Double? = null,
    ): Result<GenericRefundResponse> {
        return try {
            val response = walletApi.approveRefund(
                ApproveRefundRequest(
                    refundRequestId = refundRequestId,
                    pendingHoldId = pendingHoldId,
                    refundAmount = refundAmount,
                )
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
