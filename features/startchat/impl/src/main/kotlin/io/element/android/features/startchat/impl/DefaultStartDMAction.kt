/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.startchat.impl

import androidx.compose.runtime.MutableState
import dev.zacsweers.metro.ContributesBinding
import im.vector.app.features.analytics.plan.CreatedRoom
import io.element.android.features.startchat.api.ConfirmingStartDmWithMatrixUser
import io.element.android.features.startchat.api.ConsultantChatNotAllowedException
import io.element.android.features.startchat.api.StartDMAction
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.StartDMResult
import io.element.android.libraries.matrix.api.room.startDM
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.services.analytics.api.AnalyticsService
import io.element.android.libraries.network.wallet.WalletService

@ContributesBinding(SessionScope::class)
class DefaultStartDMAction(
    private val matrixClient: MatrixClient,
    private val analyticsService: AnalyticsService,
    private val walletService: WalletService,
) : StartDMAction {
    override suspend fun execute(
        matrixUser: MatrixUser,
        createIfDmDoesNotExist: Boolean,
        actionState: MutableState<AsyncAction<RoomId>>,
    ) {
        actionState.value = AsyncAction.Loading
        // Consultants may only chat with clients, so a consultant must not CREATE a DM with another
        // consultant. Callers only create after confirming DmDoesNotExist below (which such a pair
        // never reaches); this guard covers any caller that asks to create directly.
        if (createIfDmDoesNotExist && isConsultantToConsultant(matrixUser.userId)) {
            actionState.value = AsyncAction.Failure(ConsultantChatNotAllowedException())
            return
        }
        when (val result = matrixClient.startDM(matrixUser.userId, createIfDmDoesNotExist)) {
            is StartDMResult.Success -> {
                if (result.isNew) {
                    analyticsService.capture(CreatedRoom(isDM = true))
                    // NOTE: We intentionally do NOT initiate a wallet hold here.
                    // The hold is created on the first outgoing message from a normal user
                    // (see MessageComposerPresenter.initiateHoldIfNeeded), so:
                    //   - creating / accepting a DM never debits
                    //   - a consultant initiating the conversation never debits
                    //   - a normal user's first message (initiation OR reply) within a 24h
                    //     window for this (client, consultant) pair debits exactly once
                    //     (server-side idempotency in pending-holds.service.initiateHold)
                }
                actionState.value = AsyncAction.Success(result.roomId)
            }
            is StartDMResult.Failure -> {
                actionState.value = AsyncAction.Failure(result.throwable)
            }
            StartDMResult.DmDoesNotExist -> {
                // An EXISTING consultant <-> consultant DM still opens (its composer shows the
                // warning instead); only creating a new one is refused, with a warning.
                actionState.value = if (isConsultantToConsultant(matrixUser.userId)) {
                    AsyncAction.Failure(ConsultantChatNotAllowedException())
                } else {
                    ConfirmingStartDmWithMatrixUser(matrixUser = matrixUser)
                }
            }
        }
    }

    /** True only when BOTH users are confirmed consultants; an unknown role fails open. */
    private suspend fun isConsultantToConsultant(otherUserId: UserId): Boolean {
        val isCurrentUserConsultant = walletService.isCurrentUserConsultant.value
            ?: walletService.isUserConsultant(matrixClient.sessionId.value)
        if (isCurrentUserConsultant != true) return false
        return walletService.isUserConsultant(otherUserId.value) == true
    }
}
