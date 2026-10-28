/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.topbars

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.zacsweers.metro.Inject
import io.element.android.libraries.dateformatter.api.DateFormatter
import io.element.android.libraries.dateformatter.api.DateFormatterMode
import io.element.android.libraries.network.presence.MatrixPresenceService
import io.element.android.libraries.network.presence.PresencePrivacy
import io.element.android.libraries.network.presence.UserPresence
import io.element.android.libraries.network.wallet.WalletService
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

/** What the DM top bar shows under the partner's name. */
data class DmPeerStatus(
    /** The partner's designation when they are a consultant ("Consultant" by default); null otherwise. */
    val badge: String? = null,
    /** WhatsApp-style "online" / "last seen today at 10:30"; null when unknown or not shared. */
    val presence: String? = null,
)

/**
 * Resolves [DmPeerStatus] for the other person in a DM: their consultant designation from the
 * Wallet-API, and their presence, polled from the homeserver while the chat is on screen.
 *
 * Presence honours both people's "Last seen and online" choice, the WhatsApp way: the partner's
 * status shows only if they share it with this user, AND this user shares the same kind of status
 * (last seen / online) with them.
 */
@Inject
class DmPeerStatusPresenter(
    private val walletService: WalletService,
    private val presenceService: MatrixPresenceService,
    private val dateFormatter: DateFormatter,
    private val sessionPreferencesStore: SessionPreferencesStore,
) {
    @Composable
    fun present(sessionId: String, peerId: String?, isPeerJoined: Boolean): DmPeerStatus {
        val coroutineScope = rememberCoroutineScope()
        // Seeded from the cache (the chat list usually looked the partner up already) so the badge
        // is there at first paint.
        var isConsultant by remember(peerId) { mutableStateOf(peerId?.let(walletService::getCachedIsConsultant)) }
        var designation by remember(peerId) { mutableStateOf(peerId?.let(walletService::getCachedDesignation)) }
        // null = not known yet; the partner's presence stays hidden until it is (fail closed).
        var peerPrivacy by remember(peerId) { mutableStateOf(peerId?.let(walletService::getCachedPresencePrivacy)) }
        var presence by remember(peerId) { mutableStateOf<UserPresence?>(null) }
        val isSharingPresence by remember { sessionPreferencesStore.isSharePresenceEnabled() }.collectAsState(initial = true)
        val myPrivacy by walletService.currentUserPresencePrivacy.collectAsState()

        suspend fun refreshPeerProfile(peer: String) {
            if (walletService.refreshPeerProfile(peer)) {
                isConsultant = walletService.getCachedIsConsultant(peer)
                designation = walletService.getCachedDesignation(peer)
                peerPrivacy = walletService.getCachedPresencePrivacy(peer)
            }
        }

        LaunchedEffect(peerId) {
            // Re-read once per chat open so a designation the consultant just changed shows up.
            peerId?.let { refreshPeerProfile(it) }
        }

        // Presence is polled only while the chat is resumed and the master "Share presence" switch
        // is on. The partner's privacy choice is re-read now and then so a change applies without
        // reopening the chat.
        LifecycleResumeEffect(peerId, isSharingPresence) {
            val peer = peerId
            val job = if (peer != null && isSharingPresence) {
                coroutineScope.launch {
                    var tick = 0
                    while (isActive) {
                        if (tick > 0 && tick % PRIVACY_REFRESH_EVERY_POLLS == 0) refreshPeerProfile(peer)
                        tick++
                        // A failed poll keeps the last known value rather than blanking the label.
                        presenceService.getPresence(sessionId, peer)?.let { presence = it }
                        delay(PRESENCE_POLL_INTERVAL)
                    }
                }
            } else {
                presence = null
                null
            }
            onPauseOrDispose { job?.cancel() }
        }

        val label = if (peerId != null) {
            presence?.let {
                visiblePresenceLabel(
                    presence = it,
                    myId = sessionId,
                    peerId = peerId,
                    isContact = isPeerJoined,
                    myPrivacy = myPrivacy ?: PresencePrivacy(),
                    peerPrivacy = peerPrivacy,
                    now = System.currentTimeMillis(),
                    dateFormatter = dateFormatter,
                )
            }
        } else {
            null
        }
        return DmPeerStatus(
            badge = if (isConsultant == true) designation ?: WalletService.DEFAULT_CONSULTANT_DESIGNATION else null,
            presence = label,
        )
    }

    private companion object {
        val PRESENCE_POLL_INTERVAL = 20.seconds

        /** Re-read the partner's privacy choice every 3 polls (about once a minute). */
        const val PRIVACY_REFRESH_EVERY_POLLS = 3
    }
}

/**
 * The label for the partner's [presence], or null when either person's privacy choice hides it.
 * "Online" is never downgraded to "last seen just now": if online is hidden, so is last seen,
 * because an online audience always includes the last-seen audience.
 */
internal fun visiblePresenceLabel(
    presence: UserPresence,
    myId: String,
    peerId: String,
    isContact: Boolean,
    myPrivacy: PresencePrivacy,
    peerPrivacy: PresencePrivacy?,
    now: Long,
    dateFormatter: DateFormatter,
): String? {
    peerPrivacy ?: return null
    return if (presence.isOnline) {
        val canSeeOnline = peerPrivacy.sharesOnlineWith(ownerId = peerId, viewerId = myId, isContact = isContact) &&
            myPrivacy.sharesOnlineWith(ownerId = myId, viewerId = peerId, isContact = isContact)
        "online".takeIf { canSeeOnline }
    } else {
        val canSeeLastSeen = peerPrivacy.sharesLastSeenWith(ownerId = peerId, viewerId = myId, isContact = isContact) &&
            myPrivacy.sharesLastSeenWith(ownerId = myId, viewerId = peerId, isContact = isContact)
        if (canSeeLastSeen) formatLastSeen(presence.lastActiveTs, now, dateFormatter) else null
    }
}

/**
 * Short enough for the space under the name next to the call / refund buttons:
 * "last seen today at 10:30 AM", "last seen yesterday at …", "last seen Thu at …" within the past
 * week, and "last seen 23/10/26, 10:30 AM" (locale date order) before that.
 */
internal fun formatLastSeen(lastActiveTs: Long?, now: Long, dateFormatter: DateFormatter): String? {
    val lastActive = lastActiveTs ?: return null
    val time = dateFormatter.format(lastActive, DateFormatterMode.TimeOnly)
    return when (val days = calendarDaysBetween(lastActive, now)) {
        0 -> "last seen today at $time"
        1 -> "last seen yesterday at $time"
        else -> if (days < DAYS_IN_WEEK) {
            "last seen ${formatWithSkeleton(lastActive, "EEE")} at $time"
        } else {
            "last seen ${formatWithSkeleton(lastActive, "ddMMyy")}, $time"
        }
    }
}

/** Formats [timestamp] with the device locale's best pattern for [skeleton] (e.g. "EEE" -> "Thu"). */
private fun formatWithSkeleton(timestamp: Long, skeleton: String): String {
    val locale = Locale.getDefault()
    val pattern = DateFormat.getBestDateTimePattern(locale, skeleton) ?: skeleton
    return SimpleDateFormat(pattern, locale).format(Date(timestamp))
}

/** Whole calendar days from [from] to [to] in the device time zone; never negative (clock skew). */
private fun calendarDaysBetween(from: Long, to: Long): Int {
    fun startOfDay(timestamp: Long) = Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    // Rounded rather than truncated: a day is 23 or 25 hours across a DST change.
    val days = (startOfDay(to) - startOfDay(from)) / DAY_MILLIS.toDouble()
    return days.roundToInt().coerceAtLeast(0)
}

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
private const val DAYS_IN_WEEK = 7
