/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.clarivaauth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.login.impl.R
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.ButtonSize
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.network.wallet.IfscStatus

/**
 * Second step of Google sign-up for a consultant: the same optional chat rate
 * and bank account the email sign-up form asks for. Google sign-in has no form
 * of its own, so without this a Google consultant could only add them later.
 *
 * The account is created only from here ("Continue" or "Skip for now"), so
 * going "Back" to the role question still creates nothing.
 */
@Composable
internal fun ClarivaGooglePayoutDialog(
    prompt: GoogleRolePrompt,
    setup: ConsultantSetupForm,
    ifscStatus: IfscStatus,
    eventSink: (ClarivaAuthEvents) -> Unit,
) {
    val canContinue = setup.isValid && !ifscStatus.blocksSave

    fun confirm() {
        if (canContinue) eventSink(ClarivaAuthEvents.ConfirmGoogleRole(isConsultant = true))
    }

    Dialog(
        // Back returns to the role question; an outside tap must not discard a
        // sign-up that has already been through the Google sheet.
        onDismissRequest = { eventSink(ClarivaAuthEvents.BackToGoogleRole) },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = ElementTheme.colors.bgCanvasDefault,
        ) {
            Column(modifier = Modifier.padding(top = 24.dp)) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                ) {
                    SetupHeader(
                        subtitle = prompt.email?.let {
                            stringResource(R.string.clariva_google_payout_subtitle_with_email, it)
                        } ?: stringResource(R.string.clariva_signup_setup_subtitle),
                    )
                    Spacer(Modifier.height(16.dp))
                    ConsultantSetupFields(
                        setup = setup,
                        ifscStatus = ifscStatus,
                        enabled = true,
                        holderNameHint = prompt.name,
                        eventSink = eventSink,
                        onDone = ::confirm,
                        // Skip lives in the button row below, next to Continue.
                        onSkip = null,
                        showHeader = false,
                    )
                }
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Button(
                        text = stringResource(R.string.clariva_google_role_confirm),
                        onClick = ::confirm,
                        enabled = canContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clariva-google-payout-continue"),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            text = stringResource(R.string.clariva_google_payout_back),
                            onClick = { eventSink(ClarivaAuthEvents.BackToGoogleRole) },
                            size = ButtonSize.Medium,
                            modifier = Modifier.testTag("clariva-google-payout-back"),
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            text = stringResource(R.string.clariva_signup_setup_skip),
                            onClick = {
                                eventSink(ClarivaAuthEvents.ConfirmGoogleRole(isConsultant = true, skipPayoutSetup = true))
                            },
                            size = ButtonSize.Medium,
                            modifier = Modifier.testTag("clariva-google-payout-skip"),
                        )
                    }
                }
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun ClarivaGooglePayoutDialogPreview() = ElementPreview {
    ClarivaGooglePayoutDialog(
        prompt = GoogleRolePrompt(idToken = "tok", email = "jane@example.com", name = "Jane Doe", isAskingPayouts = true),
        setup = ConsultantSetupForm(maxCredits = "50", ifscCode = "sbin"),
        ifscStatus = IfscStatus.None,
        eventSink = {},
    )
}
