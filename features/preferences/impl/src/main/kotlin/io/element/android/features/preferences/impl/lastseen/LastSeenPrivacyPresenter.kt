/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.lastseen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.network.presence.LastSeenAudience
import io.element.android.libraries.network.presence.PresencePrivacy
import io.element.android.libraries.network.wallet.WalletService
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

@Inject
class LastSeenPrivacyPresenter(
    private val matrixClient: MatrixClient,
    private val walletService: WalletService,
) : Presenter<LastSeenPrivacyState> {
    @Composable
    override fun present(): LastSeenPrivacyState {
        val coroutineScope = rememberCoroutineScope()
        val myId = matrixClient.sessionId.value
        val savedPrivacy by walletService.currentUserPresencePrivacy.collectAsState()
        // Shown while a change is being saved, then dropped, so a failed save reverts the screen.
        var pendingPrivacy by remember { mutableStateOf<PresencePrivacy?>(null) }
        val saveAction: MutableState<AsyncAction<Unit>> = remember { mutableStateOf(AsyncAction.Uninitialized) }
        var isPickingExceptions by remember { mutableStateOf(false) }
        val contacts by produceState<ImmutableList<MatrixUser>?>(initialValue = null) { value = loadContacts() }

        LaunchedEffect(Unit) {
            // Normally loaded with the wallet at startup; this covers a startup fetch that failed.
            if (savedPrivacy == null) walletService.refreshBalance(myId)
        }

        val privacy = pendingPrivacy ?: savedPrivacy ?: PresencePrivacy()
        val excludedUserIds = contacts.orEmpty()
            .map { it.userId }
            .filter { PresencePrivacy.exceptToken(myId, it.value) in privacy.lastSeenExcept }
            .toImmutableSet()

        fun save(newPrivacy: PresencePrivacy) = coroutineScope.launch {
            pendingPrivacy = newPrivacy
            saveAction.runCatchingUpdatingState {
                walletService.updatePresencePrivacy(myId, newPrivacy).getOrThrow()
            }
            pendingPrivacy = null
        }

        fun handleEvent(event: LastSeenPrivacyEvents) {
            when (event) {
                is LastSeenPrivacyEvents.SetLastSeenAudience -> {
                    if (event.audience == LastSeenAudience.MY_CONTACTS_EXCEPT) {
                        isPickingExceptions = true
                    } else if (event.audience != privacy.lastSeen) {
                        save(privacy.copy(lastSeen = event.audience, lastSeenExcept = emptySet()))
                    }
                }
                is LastSeenPrivacyEvents.SetOnlineAudience -> {
                    if (event.audience != privacy.online) save(privacy.copy(online = event.audience))
                }
                is LastSeenPrivacyEvents.SaveExceptions -> {
                    isPickingExceptions = false
                    // Keep exclusions of people missing from the list (e.g. a chat not loaded yet).
                    val listedTokens = contacts.orEmpty().map { PresencePrivacy.exceptToken(myId, it.userId.value) }.toSet()
                    val currentTokens = privacy.lastSeenExcept.takeIf { privacy.lastSeen == LastSeenAudience.MY_CONTACTS_EXCEPT }.orEmpty()
                    val newTokens = event.excludedUserIds.map { PresencePrivacy.exceptToken(myId, it.value) }
                    save(
                        privacy.copy(
                            lastSeen = LastSeenAudience.MY_CONTACTS_EXCEPT,
                            lastSeenExcept = (currentTokens - listedTokens) + newTokens,
                        )
                    )
                }
                LastSeenPrivacyEvents.CancelExceptions -> isPickingExceptions = false
                LastSeenPrivacyEvents.ClearSaveAction -> saveAction.value = AsyncAction.Uninitialized
            }
        }

        return LastSeenPrivacyState(
            privacy = privacy,
            contacts = contacts,
            excludedUserIds = excludedUserIds,
            isPickingExceptions = isPickingExceptions,
            saveAction = saveAction.value,
            eventSink = ::handleEvent,
        )
    }

    /** The user's "contacts": everyone they have a DM with that both of them have joined, by name. */
    private suspend fun loadContacts(): ImmutableList<MatrixUser> {
        val summaries = withTimeoutOrNull(CONTACTS_TIMEOUT) {
            matrixClient.roomListService.allRooms.summaries.first()
        }.orEmpty()
        return summaries
            .filter {
                it.info.isDirect &&
                    it.info.currentUserMembership == CurrentUserMembership.JOINED &&
                    it.info.joinedMembersCount == 2L
            }
            .mapNotNull { summary -> summary.info.heroes.firstOrNull { it.userId != matrixClient.sessionId } }
            .distinctBy { it.userId }
            .sortedBy { (it.displayName ?: it.userId.value).lowercase() }
            .toImmutableList()
    }

    private companion object {
        val CONTACTS_TIMEOUT = 5.seconds
    }
}
