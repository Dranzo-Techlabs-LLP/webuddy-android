/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.datasource

import dev.zacsweers.metro.Inject
import io.element.android.features.home.impl.model.LatestEvent
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.model.RoomSummaryDisplayType
import io.element.android.libraries.core.extensions.orEmpty
import io.element.android.libraries.dateformatter.api.DateFormatter
import io.element.android.libraries.dateformatter.api.DateFormatterMode
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.eventformatter.api.RoomLatestEventFormatter
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.room.isDm
import io.element.android.libraries.matrix.api.roomlist.LatestEventValue
import io.element.android.libraries.matrix.api.roomlist.RoomSummary
import io.element.android.libraries.matrix.ui.model.getAvatarData
import io.element.android.libraries.matrix.ui.model.toInviteSender
import io.element.android.libraries.network.wallet.WalletService
import kotlinx.collections.immutable.toImmutableList
import timber.log.Timber

@Inject
class RoomListRoomSummaryFactory(
    private val dateFormatter: DateFormatter,
    private val roomLatestEventFormatter: RoomLatestEventFormatter,
    private val walletService: WalletService,
) {
    suspend fun create(roomSummary: RoomSummary): RoomListRoomSummary {
        val roomInfo = roomSummary.info
        val avatarData = roomInfo.getAvatarData(size = AvatarSize.RoomListItem)
        val heroUserId = if (roomSummary.isOneToOne) roomInfo.heroes.firstOrNull()?.userId else null
        
        Timber.d("Processing room ${roomInfo.name}. Hero: ${heroUserId?.value}")
        
        // Per-row max-credits is the chat partner's rate. It only makes sense to display when the
        // OTHER party is a consultant — clients don't have a chat rate. This holds regardless of
        // whether the local user is a client (sees what they'll pay) or a consultant (sees the
        // peer consultant's rate, which is a no-op for self-billing).
        val credits = heroUserId?.let { walletService.getRecipientChatRate(it.value) }

        // For consultants, mark the row if the other party has an open refund request waiting.
        // The pending set is populated by HomePresenter on home mount via
        // walletService.refreshPendingRefundRequests(); we just look it up here (O(1)).
        val isConsultant = walletService.isCurrentUserConsultant.value == true
        val hasPendingRefundRequest = isConsultant && heroUserId != null &&
            heroUserId.value in walletService.pendingRefundRequestClients.value

        return RoomListRoomSummary(
            id = roomSummary.roomId.value,
            roomId = roomSummary.roomId,
            name = roomInfo.name,
            numberOfUnreadMessages = roomInfo.numUnreadMessages,
            numberOfUnreadMentions = roomInfo.numUnreadMentions,
            numberOfUnreadNotifications = roomInfo.numUnreadNotifications,
            isMarkedUnread = roomInfo.isMarkedUnread,
            timestamp = dateFormatter.format(
                timestamp = roomSummary.latestEventTimestamp,
                mode = DateFormatterMode.TimeOrDate,
                useRelative = true,
            ),
            latestEvent = computeLatestEvent(roomSummary.latestEvent, roomInfo.isDm),
            avatarData = avatarData,
            userDefinedNotificationMode = roomInfo.userDefinedNotificationMode,
            hasRoomCall = roomInfo.hasRoomCall,
            isDirect = roomInfo.isDirect,
            isFavorite = roomInfo.isFavorite,
            inviteSender = roomInfo.inviter?.toInviteSender(),
            isDm = roomInfo.isDm,
            canonicalAlias = roomInfo.canonicalAlias,
            displayType = when (roomInfo.currentUserMembership) {
                CurrentUserMembership.INVITED -> {
                    RoomSummaryDisplayType.INVITE
                }
                CurrentUserMembership.KNOCKED -> {
                    RoomSummaryDisplayType.KNOCKED
                }
                else -> {
                    RoomSummaryDisplayType.ROOM
                }
            },
            heroes = roomInfo.heroes.map { user ->
                user.getAvatarData(size = AvatarSize.RoomListItem)
            }.toImmutableList(),
            isTombstoned = roomInfo.successorRoom != null,
            isSpace = roomInfo.isSpace,
            heroUserId = heroUserId,
            credits = credits,
            hasPendingRefundRequest = hasPendingRefundRequest,
        )
    }

    private fun computeLatestEvent(latestEvent: LatestEventValue, dm: Boolean): LatestEvent {
        return when (latestEvent) {
            is LatestEventValue.None -> {
                LatestEvent.None
            }
            is LatestEventValue.Local -> {
                if (latestEvent.isSending) {
                    val content = roomLatestEventFormatter.format(latestEvent, dm).orEmpty()
                    LatestEvent.Sending(
                        content = content,
                    )
                } else {
                    LatestEvent.Error
                }
            }
            is LatestEventValue.Remote -> {
                val content = roomLatestEventFormatter.format(latestEvent, dm).orEmpty()
                LatestEvent.Synced(
                    content = content,
                )
            }
        }
    }
}
