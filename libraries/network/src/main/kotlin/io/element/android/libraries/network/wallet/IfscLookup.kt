/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.network.wallet

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.HttpException
import retrofit2.http.GET
import retrofit2.http.Path
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Razorpay's public IFSC directory: 200 with the branch, or 404 for a code that does not exist. */
interface IfscApi {
    @GET("{ifsc}")
    suspend fun lookup(@Path("ifsc") ifsc: String): IfscApiResponse

    companion object {
        const val BASE_URL = "https://ifsc.razorpay.com/"
    }
}

@Serializable
data class IfscApiResponse(
    @SerialName("BANK") val bank: String? = null,
    @SerialName("BRANCH") val branch: String? = null,
    @SerialName("CITY") val city: String? = null,
    @SerialName("STATE") val state: String? = null,
)

/** The bank branch an IFSC belongs to; [branch] and [city] are title-cased for display. */
data class IfscBranch(
    val bank: String,
    val branch: String,
    val city: String?,
) {
    /** e.g. "State Bank of India · Hajiganj, Patna". */
    val summary: String get() = "$bank · " + listOfNotNull(branch.takeIf { it.isNotBlank() }, city).joinToString(", ")
}

/** What the payout forms know about the IFSC being typed. */
sealed interface IfscStatus {
    /** Empty or not yet a well-formed code: nothing to look up. */
    data object None : IfscStatus
    data object Checking : IfscStatus
    data class Found(val branch: IfscBranch) : IfscStatus
    /** Well-formed, but no such branch: blocks saving. */
    data object NotFound : IfscStatus
    /** The directory could not be reached; saving is allowed and the API checks again. */
    data object Unavailable : IfscStatus

    /** Wrong, or still being checked: hold off saving. An unreachable directory does not block. */
    val blocksSave: Boolean get() = this is NotFound || this is Checking
}

/**
 * Looks IFSCs up while a consultant types them, so a mistyped code is caught
 * before it becomes a failed payout, and the bank and branch fill themselves in.
 */
@Inject
@SingleIn(AppScope::class)
class IfscLookup(
    private val ifscApi: IfscApi,
) {
    // Only definite answers are cached (null = no such IFSC); an unreachable directory is retried.
    private val cache = ConcurrentHashMap<String, Result<IfscBranch?>>()

    suspend fun lookup(rawIfsc: String): IfscStatus {
        val ifsc = rawIfsc.trim().uppercase(Locale.ROOT)
        if (!BankAccountRules.isValidIfsc(ifsc)) return IfscStatus.None
        cache[ifsc]?.let { cached ->
            return cached.getOrNull()?.let { IfscStatus.Found(it) } ?: IfscStatus.NotFound
        }
        return try {
            val response = ifscApi.lookup(ifsc)
            val bank = response.bank
            if (bank.isNullOrBlank()) {
                IfscStatus.Unavailable
            } else {
                val branch = IfscBranch(
                    bank = bank,
                    branch = response.branch.orEmpty().titleCase(),
                    city = response.city?.titleCase()?.takeIf { it.isNotBlank() },
                )
                cache[ifsc] = Result.success(branch)
                IfscStatus.Found(branch)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 404) {
                cache[ifsc] = Result.success(null)
                IfscStatus.NotFound
            } else {
                Timber.w(e, "IFSC lookup failed with HTTP ${e.code()}")
                IfscStatus.Unavailable
            }
        } catch (e: Exception) {
            Timber.w(e, "IFSC lookup unavailable")
            IfscStatus.Unavailable
        }
    }

    private fun String.titleCase(): String = lowercase(Locale.ROOT)
        .split(' ')
        .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.ROOT) } }
        .trim()
}
