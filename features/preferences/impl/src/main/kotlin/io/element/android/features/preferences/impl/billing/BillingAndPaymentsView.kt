/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.billing

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
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
    val snackbarHostState = remember { SnackbarHostState() }

    // Local field state so typing is smooth; reset whenever the loaded value changes.
    var maxCreditsInput by remember(state.maxCredits) {
        mutableStateOf(state.maxCredits?.toString() ?: "")
    }
    var isFocused by remember { mutableStateOf(false) }
    // Distinguishes a real save (user left the field / pressed Done) from the presenter's
    // initial refreshBalance success on screen open, so the "saved" confirmation only shows
    // after an actual edit.
    var saveRequested by remember { mutableStateOf(false) }

    // Surface the outcome of a save: a confirmation on success, an error otherwise. Guarded by
    // saveRequested so the initial balance refresh doesn't pop a spurious "saved" toast.
    LaunchedEffect(state.walletAction) {
        if (!saveRequested) return@LaunchedEffect
        when (state.walletAction) {
            is AsyncAction.Success -> {
                saveRequested = false
                // Show the confirmation BEFORE clearing the action: clearing changes this
                // LaunchedEffect's key and would cancel showSnackbar before it's seen.
                snackbarHostState.showSnackbar("Max credits saved")
                state.eventSink(AdvancedSettingsEvents.ClearWalletActionError)
            }
            is AsyncAction.Failure -> {
                saveRequested = false
                snackbarHostState.showSnackbar("Couldn't save max credits. Please try again.")
                state.eventSink(AdvancedSettingsEvents.ClearWalletActionError)
            }
            else -> Unit
        }
    }

    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = "Billing and Payments",
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    ) {
        PreferenceCategory(
            title = "Wallet",
            showTopDivider = false,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Max Credits",
                    style = ElementTheme.typography.fontBodyLgMedium,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(16.dp))

                if (state.maxCredits == null || state.walletAction.isLoading()) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = ElementTheme.colors.iconPrimary
                    )
                } else {
                    BasicTextField(
                        value = maxCreditsInput,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                maxCreditsInput = newValue
                                newValue.toIntOrNull()?.let {
                                    state.eventSink(AdvancedSettingsEvents.SetMaxCredits(it))
                                }
                            }
                        },
                        modifier = Modifier
                            .width(80.dp)
                            .onFocusChanged { focusState ->
                                if (isFocused && !focusState.isFocused) {
                                    // Focus lost (keyboard closed / back / tapped away): auto-save if
                                    // the field is filled and the value actually changed. The
                                    // !saveRequested guard prevents a second save when the field is
                                    // disposed (swapped for the spinner) mid-save by the Done handler.
                                    if (!saveRequested &&
                                        maxCreditsInput.isNotEmpty() &&
                                        maxCreditsInput != (state.originalMaxCredits?.toString() ?: "")) {
                                        saveRequested = true
                                        state.eventSink(AdvancedSettingsEvents.SaveMaxCredits)
                                    }
                                }
                                isFocused = focusState.isFocused
                            }
                            .border(
                                width = 1.dp,
                                color = ElementTheme.colors.borderInteractiveSecondary,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(8.dp),
                        textStyle = ElementTheme.typography.fontBodyLgRegular.copy(
                            color = ElementTheme.colors.textPrimary,
                            textAlign = TextAlign.Start
                        ),
                        cursorBrush = SolidColor(ElementTheme.colors.iconPrimary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (!saveRequested &&
                                    maxCreditsInput.isNotEmpty() &&
                                    maxCreditsInput != (state.originalMaxCredits?.toString() ?: "")) {
                                    saveRequested = true
                                    state.eventSink(AdvancedSettingsEvents.SaveMaxCredits)
                                }
                            }
                        ),
                        singleLine = true,
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
                onClick = onBankDetailsClick
            )
        }
    }
}
