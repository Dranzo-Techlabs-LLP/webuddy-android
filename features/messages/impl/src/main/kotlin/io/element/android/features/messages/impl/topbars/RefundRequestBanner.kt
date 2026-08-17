/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.topbars

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * Format a money amount for display: drop the fractional part when it is a whole number
 * (e.g. 20.0 -> "20"), otherwise keep it (e.g. 12.5 -> "12.5").
 */
private fun formatAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

@Composable
internal fun RefundRequestBanner(
    isInProgress: Boolean,
    onApprove: (refundAmount: Double?) -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
    heldAmount: Double? = null,
) {
    val showApproveDialog = remember { mutableStateOf(false) }
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
                text = "The client has asked for a refund. Approve to return the held amount (in full or in part) " +
                    "immediately, or reject to keep the funds (transferred at the end of the hold window).",
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    text = "Approve",
                    onClick = { showApproveDialog.value = true },
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

    if (showApproveDialog.value) {
        ApproveRefundDialog(
            heldAmount = heldAmount,
            isInProgress = isInProgress,
            onConfirm = { refundAmount ->
                showApproveDialog.value = false
                onApprove(refundAmount)
            },
            onDismiss = { showApproveDialog.value = false },
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

/**
 * Approve dialog offering a FULL refund (whole held amount back to the client) or a PARTIAL
 * refund of a typed amount (the consultant keeps the remainder, credited immediately). The
 * amount is validated to be > 0 and at most the held amount before Approve is enabled.
 * [onConfirm] receives null for a full refund, or the positive partial amount.
 */
@Composable
private fun ApproveRefundDialog(
    heldAmount: Double?,
    isInProgress: Boolean,
    onConfirm: (refundAmount: Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    var isPartial by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }

    // Round to 2 decimals exactly like the backend (round2) so the client never enables Approve
    // for a value the server will reject — e.g. 0.004 rounds to 0.00 and would 400.
    val parsedAmount = amountText.trim().toDoubleOrNull()
    val roundedAmount = parsedAmount?.let { kotlin.math.round(it * 100) / 100 }
    val amountValid = when {
        !isPartial -> true
        roundedAmount == null -> false
        roundedAmount <= 0.0 -> false
        heldAmount != null && roundedAmount > heldAmount + 1e-6 -> false
        else -> true
    }
    val showAmountError = isPartial && amountText.isNotBlank() && !amountValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Approve Refund") },
        text = {
            Column {
                val fullLabel = heldAmount?.let { "Full refund (₹${formatAmount(it)})" } ?: "Full refund"
                RefundOptionRow(
                    selected = !isPartial,
                    label = fullLabel,
                    onSelect = { isPartial = false },
                )
                RefundOptionRow(
                    selected = isPartial,
                    label = "Partial refund",
                    onSelect = { isPartial = true },
                )
                if (isPartial) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { new ->
                            // Accept only digits and a single decimal point.
                            if (new.isEmpty() || new.matches(Regex("^\\d*\\.?\\d*$"))) {
                                amountText = new
                            }
                        },
                        label = { Text("Refund amount") },
                        singleLine = true,
                        isError = showAmountError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = {
                            val helper = when {
                                showAmountError && heldAmount != null ->
                                    "Enter an amount greater than 0 and at most ₹${formatAmount(heldAmount)}."
                                showAmountError ->
                                    "Enter an amount greater than 0."
                                heldAmount != null ->
                                    "The client gets this back; you keep the rest of ₹${formatAmount(heldAmount)}."
                                else -> "The client gets this back; you keep the remainder."
                            }
                            Text(helper)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "The full held amount will be returned to the client immediately.",
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(if (isPartial) roundedAmount else null) },
                enabled = !isInProgress && amountValid,
            ) {
                Text("Approve")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun RefundOptionRow(
    selected: Boolean,
    label: String,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textPrimary,
        )
    }
}
