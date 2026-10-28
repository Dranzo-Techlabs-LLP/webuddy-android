/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav.di

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.coroutine.childScope
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.network.presence.MatrixPresenceService
import io.element.android.libraries.network.wallet.WalletService
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import io.element.android.services.appnavstate.api.AppForegroundStateService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Shows this account as "online" to its chat partners while the app is in use, like WhatsApp.
 *
 * Simplified sliding sync never marks a user online, so this publishes "online" every
 * [ONLINE_REFRESH] while the app is in the foreground or in a call — inside Synapse's 30 s
 * online timeout and above its presence rate limit (one update per 10 s) — and "offline" as soon
 * as the app is backgrounded. If the process dies, Synapse's timeout takes the account offline.
 * Nothing is published while the user has turned "Share presence" off, or while their
 * "Last seen and online" choice lets nobody see it (chat partners' apps also honour that choice;
 * this only stops the homeserver from holding a status nobody may see).
 */
@Inject
@SingleIn(SessionScope::class)
class PresenceHeartbeat(
    private val matrixClient: MatrixClient,
    private val appForegroundStateService: AppForegroundStateService,
    private val sessionPreferencesStore: SessionPreferencesStore,
    private val presenceService: MatrixPresenceService,
    private val walletService: WalletService,
    @SessionCoroutineScope sessionCoroutineScope: CoroutineScope,
    dispatchers: CoroutineDispatchers,
) {
    private val coroutineScope = sessionCoroutineScope.childScope(dispatchers.io, "PresenceHeartbeat")
    private var job: Job? = null

    fun start() {
        if (job != null) return
        val sessionId = matrixClient.sessionId.value
        job = coroutineScope.launch {
            combine(
                appForegroundStateService.isInForeground,
                appForegroundStateService.isInCall,
                sessionPreferencesStore.isSharePresenceEnabled(),
                // Not loaded yet counts as shared (the default), as before this choice existed.
                walletService.currentUserPresencePrivacy.map { it?.sharesWithAnyone != false },
            ) { isInForeground, isInCall, isSharingPresence, isVisibleToAnyone ->
                when {
                    !isSharingPresence || !isVisibleToAnyone -> Presence.Hidden
                    isInForeground || isInCall -> Presence.Online
                    else -> Presence.Offline
                }
            }
                .distinctUntilChanged()
                .collectLatest { presence ->
                    when (presence) {
                        Presence.Online -> while (true) {
                            presenceService.setPresence(sessionId, online = true)
                            delay(ONLINE_REFRESH)
                        }
                        // Turning sharing off also ends any "online" shown right now.
                        Presence.Offline, Presence.Hidden -> presenceService.setPresence(sessionId, online = false)
                    }
                }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private enum class Presence { Online, Offline, Hidden }

    private companion object {
        val ONLINE_REFRESH = 25.seconds
    }
}
