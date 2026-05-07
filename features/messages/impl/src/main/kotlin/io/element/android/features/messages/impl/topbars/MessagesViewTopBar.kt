/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.topbars

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.SummaryDuration
import io.element.android.features.messages.impl.timeline.components.CallMenuItem
import io.element.android.features.roomcall.api.RoomCallState
import io.element.android.features.roomcall.api.aStandByCallState
import io.element.android.features.roomcall.api.anOngoingCallState
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.components.avatar.anAvatarData
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.api.encryption.identity.IdentityState
import io.element.android.libraries.matrix.ui.components.aMatrixUserList
import io.element.android.libraries.matrix.ui.model.getAvatarData
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessagesViewTopBar(
    roomName: String?,
    roomAvatar: AvatarData,
    isTombstoned: Boolean,
    heroes: ImmutableList<AvatarData>,
    roomCallState: RoomCallState,
    dmUserIdentityState: IdentityState?,
    onRoomDetailsClick: () -> Unit,
    onJoinCallClick: () -> Unit,
    onBackClick: () -> Unit,
    onSummarizeClick: (SummaryDuration?) -> Unit,
    isRefundButtonVisible: Boolean,
    refundStatus: String?,
    isRefundRequestInProgress: Boolean,
    isConsultantInThisRoom: Boolean,
    onRefundClick: () -> Unit,
    onApproveRefundClick: () -> Unit,
    onRejectRefundClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        navigationIcon = {
            BackButton(onClick = onBackClick)
        },
        title = {
            val roundedCornerShape = RoundedCornerShape(8.dp)
            Row(
                modifier = Modifier
                    .clip(roundedCornerShape)
                    .clickable { onRoomDetailsClick() },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val titleModifier = Modifier.weight(1f, fill = false)
                RoomAvatarAndNameRow(
                    roomName = roomName,
                    roomAvatar = roomAvatar,
                    isTombstoned = isTombstoned,
                    heroes = heroes,
                    modifier = titleModifier
                )

                when (dmUserIdentityState) {
                    IdentityState.Verified -> {
                        Icon(
                            imageVector = CompoundIcons.Verified(),
                            tint = ElementTheme.colors.iconSuccessPrimary,
                            contentDescription = null,
                        )
                    }
                    IdentityState.VerificationViolation -> {
                        Icon(
                            imageVector = CompoundIcons.ErrorSolid(),
                            tint = ElementTheme.colors.iconCriticalPrimary,
                            contentDescription = null,
                        )
                    }
                    else -> Unit
                }
            }
        },
        actions = {
            // Consultant view: incoming refund request awaiting decision.
            if (isRefundButtonVisible && isConsultantInThisRoom && refundStatus == "requested") {
                val showApproveConfirm = remember { mutableStateOf(false) }
                val showRejectConfirm = remember { mutableStateOf(false) }
                val tint = if (!isRefundRequestInProgress) ElementTheme.colors.iconPrimary else ElementTheme.colors.iconDisabled

                IconButton(
                    onClick = { showApproveConfirm.value = true },
                    enabled = !isRefundRequestInProgress
                ) {
                    Icon(
                        imageVector = CompoundIcons.Check(),
                        contentDescription = "Approve Refund",
                        tint = tint,
                    )
                }
                IconButton(
                    onClick = { showRejectConfirm.value = true },
                    enabled = !isRefundRequestInProgress
                ) {
                    Icon(
                        imageVector = CompoundIcons.Close(),
                        contentDescription = "Reject Refund",
                        tint = tint,
                    )
                }

                if (showApproveConfirm.value) {
                    io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog(
                        title = "Approve Refund",
                        content = "The held amount will be returned to the client immediately.",
                        submitText = "Approve",
                        cancelText = "Cancel",
                        onSubmitClick = {
                            showApproveConfirm.value = false
                            onApproveRefundClick()
                        },
                        onDismiss = { showApproveConfirm.value = false }
                    )
                }
                if (showRejectConfirm.value) {
                    io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog(
                        title = "Reject Refund",
                        content = "The held amount will transfer to your wallet at the end of the hold window.",
                        submitText = "Reject",
                        cancelText = "Cancel",
                        onSubmitClick = {
                            showRejectConfirm.value = false
                            onRejectRefundClick()
                        },
                        onDismiss = { showRejectConfirm.value = false }
                    )
                }
            } else if (isRefundButtonVisible && !isConsultantInThisRoom && (refundStatus == "none" || refundStatus == null)) {
                // Client view: can request refund.
                val showRefundConfirm = remember { mutableStateOf(false) }

                IconButton(
                    onClick = { showRefundConfirm.value = true },
                    enabled = !isRefundRequestInProgress
                ) {
                    val iconTint = if (!isRefundRequestInProgress) {
                        ElementTheme.colors.iconPrimary
                    } else {
                        ElementTheme.colors.iconDisabled
                    }
                    Icon(
                        imageVector = CompoundIcons.Restart(),
                        contentDescription = "Request Refund",
                        tint = iconTint
                    )
                }

                if (showRefundConfirm.value) {
                    io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog(
                        title = "Request Refund",
                        content = "Are you sure you want to request a refund?",
                        submitText = "Request",
                        cancelText = "Cancel",
                        onSubmitClick = {
                            showRefundConfirm.value = false
                            onRefundClick()
                        },
                        onDismiss = {
                            showRefundConfirm.value = false
                        }
                    )
                }
            }

            // Chat history / AI summary menu hidden for now; re-enable once the feature is complete.
            CallMenuItem(
                roomCallState = roomCallState,
                onJoinCallClick = onJoinCallClick,
            )
            Spacer(Modifier.width(8.dp))
        },
        windowInsets = WindowInsets(0.dp)
    )
}

@Composable
private fun RoomAvatarAndNameRow(
    roomName: String?,
    roomAvatar: AvatarData,
    heroes: ImmutableList<AvatarData>,
    isTombstoned: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            avatarData = roomAvatar,
            avatarType = AvatarType.Room(
                heroes = heroes,
                isTombstoned = isTombstoned,
            ),
        )
        Text(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .semantics {
                    heading()
                },
            text = roomName ?: stringResource(CommonStrings.common_no_room_name),
            style = ElementTheme.typography.fontBodyLgMedium,
            fontStyle = FontStyle.Italic.takeIf { roomName == null },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@PreviewsDayNight
@Composable
internal fun MessagesViewTopBarPreview() = ElementPreview {
    @Composable
    fun AMessagesViewTopBar(
        roomName: String? = "Room name",
        roomAvatar: AvatarData = anAvatarData(
            name = "Room name",
            size = AvatarSize.TimelineRoom,
        ),
        isTombstoned: Boolean = false,
        heroes: ImmutableList<AvatarData> = persistentListOf(),
        roomCallState: RoomCallState = RoomCallState.Unavailable,
        dmUserIdentityState: IdentityState? = null,
    ) = MessagesViewTopBar(
        roomName = roomName,
        roomAvatar = roomAvatar,
        isTombstoned = isTombstoned,
        heroes = heroes,
        roomCallState = roomCallState,
        dmUserIdentityState = dmUserIdentityState,
        isRefundButtonVisible = false,
        refundStatus = null,
        isRefundRequestInProgress = false,
        isConsultantInThisRoom = false,
        onRefundClick = {},
        onApproveRefundClick = {},
        onRejectRefundClick = {},
        onRoomDetailsClick = {},
        onJoinCallClick = {},
        onBackClick = {},
        onSummarizeClick = {},
    )
    Column {
        AMessagesViewTopBar()
        HorizontalDivider()
        AMessagesViewTopBar(
            heroes = aMatrixUserList().map { it.getAvatarData(AvatarSize.TimelineRoom) }.toImmutableList(),
            roomCallState = anOngoingCallState(),
        )
        HorizontalDivider()
        AMessagesViewTopBar(
            roomName = null,
            roomCallState = anOngoingCallState(canJoinCall = false),
        )
        HorizontalDivider()
        AMessagesViewTopBar(
            roomName = "A DM with a very very very long name",
            roomAvatar = anAvatarData(
                size = AvatarSize.TimelineRoom,
                url = "https://some-avatar.jpg"
            ),
            roomCallState = aStandByCallState(canStartCall = false),
            dmUserIdentityState = IdentityState.Verified
        )
        HorizontalDivider()
        AMessagesViewTopBar(
            roomName = "A DM with a very very very long name",
            isTombstoned = true,
            dmUserIdentityState = IdentityState.VerificationViolation
        )
    }
}
