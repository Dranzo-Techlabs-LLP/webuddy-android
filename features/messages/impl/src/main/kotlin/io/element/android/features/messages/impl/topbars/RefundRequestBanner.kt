/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.topbars

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog

@Composable
internal fun RefundRequestBanner(
    isInProgress: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showApproveConfirm = remember { mutableStateOf(false) }
    val showRejectConfirm = remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ElementTheme.colors.bgSubtlePrimary)
            .border(1.dp, ElementTheme.colors.borderInteractiveSecondary, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CompoundIcons.InfoSolid(),
            contentDescription = null,
            tint = ElementTheme.colors.iconPrimary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Refund request",
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textPrimary,
            )
            Text(
                text = "The client has asked for a refund. Approve to return the held amount immediately, or reject to keep the funds (transferred at the end of the hold window).",
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    text = "Approve",
                    onClick = { showApproveConfirm.value = true },
                    enabled = !isInProgress,
                )
                OutlinedButton(
                    onClick = { showRejectConfirm.value = true },
                    enabled = !isInProgress,
                ) {
                    Text("Reject")
                }
            }
        }
    }

    if (showApproveConfirm.value) {
        ConfirmationDialog(
            title = "Approve Refund",
            content = "The held amount will be returned to the client immediately.",
            submitText = "Approve",
            cancelText = "Cancel",
            onSubmitClick = {
                showApproveConfirm.value = false
                onApprove()
            },
            onDismiss = { showApproveConfirm.value = false },
        )
    }
    if (showRejectConfirm.value) {
        ConfirmationDialog(
            title = "Reject Refund",
            content = "The held amount will transfer to your wallet at the end of the hold window.",
            submitText = "Reject",
            cancelText = "Cancel",
            onSubmitClick = {
                showRejectConfirm.value = false
                onReject()
            },
            onDismiss = { showRejectConfirm.value = false },
        )
    }
}
