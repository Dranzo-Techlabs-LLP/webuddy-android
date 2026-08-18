/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.roomcall.api.RoomCallState
import io.element.android.features.roomcall.api.RoomCallStateProvider
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
internal fun CallMenuItem(
    roomCallState: RoomCallState,
    onJoinCallClick: () -> Unit,
    modifier: Modifier = Modifier,
    // When non-null, tapping the "start a call" button opens a voice/video chooser and reports the
    // choice here (true = video / camera on, false = voice / camera off) instead of calling
    // onJoinCallClick. Only wired for the DM chat top bar; other callers keep the direct behaviour.
    onStartCallWithType: ((videoEnabled: Boolean) -> Unit)? = null,
    // Client credit gate: when true the client cannot afford the consultant, so the call button is
    // HIDDEN entirely — mirroring how the chat composer is replaced by the recharge banner. Never
    // true for the consultant.
    isCallRestricted: Boolean = false,
) {
    when (roomCallState) {
        RoomCallState.Unavailable -> {
            Box(modifier)
        }
        is RoomCallState.StandBy -> {
            if (isCallRestricted) {
                Box(modifier)
            } else {
                StandByCallMenuItem(
                    roomCallState = roomCallState,
                    onJoinCallClick = onJoinCallClick,
                    onStartCallWithType = onStartCallWithType,
                    modifier = modifier,
                )
            }
        }
        is RoomCallState.OnGoing -> {
            if (isCallRestricted) {
                Box(modifier)
            } else {
                OnGoingCallMenuItem(
                    roomCallState = roomCallState,
                    onJoinCallClick = onJoinCallClick,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun StandByCallMenuItem(
    roomCallState: RoomCallState.StandBy,
    onJoinCallClick: () -> Unit,
    onStartCallWithType: ((videoEnabled: Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var showChooser by remember { mutableStateOf(false) }
    IconButton(
        modifier = modifier,
        onClick = {
            if (onStartCallWithType != null) {
                showChooser = true
            } else {
                onJoinCallClick()
            }
        },
        enabled = roomCallState.canStartCall,
    ) {
        Icon(
            // A plain phone icon when the chooser is available (the tap asks voice vs video);
            // the original video-call icon for the legacy direct-start callers.
            imageVector = if (onStartCallWithType != null) CompoundIcons.VoiceCall() else CompoundIcons.VideoCallSolid(),
            contentDescription = stringResource(CommonStrings.a11y_start_call),
        )
    }

    if (showChooser && onStartCallWithType != null) {
        CallTypeChooserDialog(
            onVoiceCall = {
                showChooser = false
                onStartCallWithType(false)
            },
            onVideoCall = {
                showChooser = false
                onStartCallWithType(true)
            },
            onDismiss = { showChooser = false },
        )
    }
}

@Composable
private fun CallTypeChooserDialog(
    onVoiceCall: () -> Unit,
    onVideoCall: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start a call") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Choose how you want to call.",
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                )
                Button(
                    onClick = onVoiceCall,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = CompoundIcons.VoiceCall(),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Voice call")
                }
                Button(
                    onClick = onVideoCall,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = CompoundIcons.VideoCall(),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Video call")
                }
            }
        },
        // No affirmative action: the two options above ARE the actions.
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CommonStrings.action_cancel))
            }
        },
    )
}

@Composable
private fun OnGoingCallMenuItem(
    roomCallState: RoomCallState.OnGoing,
    onJoinCallClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!roomCallState.isUserLocallyInTheCall) {
        Button(
            onClick = onJoinCallClick,
            colors = ButtonDefaults.buttonColors(
                contentColor = ElementTheme.colors.bgCanvasDefault,
                containerColor = ElementTheme.colors.iconAccentTertiary
            ),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier = modifier.heightIn(min = 36.dp),
            enabled = roomCallState.canJoinCall,
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = CompoundIcons.VideoCallSolid(),
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(CommonStrings.action_join),
                style = ElementTheme.typography.fontBodyMdMedium
            )
            Spacer(Modifier.width(8.dp))
        }
    } else {
        // Else user is already in the call, hide the button.
        Box(modifier)
    }
}

@PreviewsDayNight
@Composable
internal fun CallMenuItemPreview(
    @PreviewParameter(RoomCallStateProvider::class) roomCallState: RoomCallState
) = ElementPreview {
    CallMenuItem(
        roomCallState = roomCallState,
        onJoinCallClick = {}
    )
}
