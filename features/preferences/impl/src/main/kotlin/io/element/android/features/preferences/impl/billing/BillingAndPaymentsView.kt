/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.billing

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.advanced.AdvancedSettingsEvents
import io.element.android.features.preferences.impl.advanced.AdvancedSettingsState
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text

@Composable
fun BillingAndPaymentsView(
    state: AdvancedSettingsState,
    onBackClick: () -> Unit,
    onBankDetailsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val savedValue = state.originalMaxCredits?.toString() ?: state.maxCredits?.toString() ?: ""

    // Local field state so typing is smooth; reset whenever the loaded value changes.
    var maxCreditsInput by remember(state.maxCredits) {
        mutableStateOf(state.maxCredits?.toString() ?: "")
    }
    var saveInFlight by remember { mutableStateOf(false) }
    var justSaved by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }

    val hasChanges = maxCreditsInput.isNotEmpty() && maxCreditsInput != savedValue

    // Resolve the outcome of an in-flight save into an inline confirmation / error. Guarded by
    // saveInFlight so the presenter's initial balance refresh never trips a spurious "saved".
    LaunchedEffect(state.walletAction) {
        if (!saveInFlight) return@LaunchedEffect
        when (state.walletAction) {
            is AsyncAction.Success -> {
                saveInFlight = false
                justSaved = true
                saveFailed = false
                state.eventSink(AdvancedSettingsEvents.ClearWalletActionError)
            }
            is AsyncAction.Failure -> {
                saveInFlight = false
                saveFailed = true
                justSaved = false
                state.eventSink(AdvancedSettingsEvents.ClearWalletActionError)
            }
            else -> Unit
        }
    }

    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = "Billing and Payments",
        snackbarHost = {},
    ) {
        PreferenceCategory(
            title = "Wallet",
            showTopDivider = false,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Max Credits",
                        style = ElementTheme.typography.fontBodyLgMedium,
                        modifier = Modifier.weight(1f),
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    if (state.maxCredits == null) {
                        // Initial load only. During a SAVE the field stays visible (Save button
                        // shows the progress) so the focused field is never disposed mid-save.
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = ElementTheme.colors.iconPrimary,
                        )
                    } else {
                        BasicTextField(
                            value = maxCreditsInput,
                            onValueChange = { newValue ->
                                if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                    maxCreditsInput = newValue
                                    // Editing again clears any prior save result banner.
                                    justSaved = false
                                    saveFailed = false
                                    newValue.toIntOrNull()?.let {
                                        state.eventSink(AdvancedSettingsEvents.SetMaxCredits(it))
                                    }
                                }
                            },
                            modifier = Modifier
                                .width(80.dp)
                                .border(
                                    width = 1.dp,
                                    color = ElementTheme.colors.borderInteractiveSecondary,
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .padding(8.dp),
                            textStyle = ElementTheme.typography.fontBodyLgRegular.copy(
                                color = ElementTheme.colors.textPrimary,
                                textAlign = TextAlign.Start,
                            ),
                            cursorBrush = SolidColor(ElementTheme.colors.iconPrimary),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                    }
                }

                // Save / Cancel show while there's an unsaved change. The result banners are
                // SEPARATE (not mutually exclusive with the buttons) so a failed save is never
                // silent: after a failure the value still differs from saved, so the Save/Cancel row
                // stays visible AND the error shows below it, letting the user retry.
                if (hasChanges) {
                    Spacer(Modifier.size(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                // Revert to the last saved value.
                                maxCreditsInput = savedValue
                                savedValue.toIntOrNull()?.let {
                                    state.eventSink(AdvancedSettingsEvents.SetMaxCredits(it))
                                }
                                justSaved = false
                                saveFailed = false
                            },
                            enabled = !saveInFlight,
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            text = if (saveInFlight) "Saving…" else "Save",
                            onClick = {
                                maxCreditsInput.toIntOrNull()?.let {
                                    state.eventSink(AdvancedSettingsEvents.SetMaxCredits(it))
                                    saveInFlight = true
                                    justSaved = false
                                    saveFailed = false
                                    state.eventSink(AdvancedSettingsEvents.SaveMaxCredits)
                                }
                            },
                            enabled = !saveInFlight,
                        )
                    }
                }
                if (justSaved) {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "Max credits saved",
                        style = ElementTheme.typography.fontBodyMdMedium,
                        color = ElementTheme.colors.textSuccessPrimary,
                    )
                }
                if (saveFailed) {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "Couldn't save max credits. Please try again.",
                        style = ElementTheme.typography.fontBodyMdMedium,
                        color = ElementTheme.colors.textCriticalPrimary,
                    )
                }
            }

            ListItem(
                headlineContent = {
                    Text(
                        text = "Bank Details",
                        style = ElementTheme.typography.fontBodyLgMedium,
                        color = ElementTheme.colors.textPrimary,
                    )
                },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Settings())),
                onClick = onBankDetailsClick,
            )
        }
    }
}
