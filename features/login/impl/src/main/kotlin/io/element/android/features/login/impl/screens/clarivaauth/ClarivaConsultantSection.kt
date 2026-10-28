/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.clarivaauth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.login.impl.R
import io.element.android.libraries.designsystem.theme.components.ButtonSize
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Switch
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TextFieldValidity

/**
 * The "I am a consultant" switch, and below it - only once it is on - the
 * optional payout setup: chat rate and bank account. Grouped in one card so it
 * reads as part of that choice, not as more mandatory sign-up fields.
 */
@Composable
internal fun ClarivaConsultantSection(
    state: ClarivaAuthState,
    firstFieldFocusRequester: FocusRequester,
    onSubmit: () -> Unit,
) {
    val eventSink = state.eventSink
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ElementTheme.colors.bgCanvasDefault.copy(alpha = 0.85f))
            .border(1.dp, ElementTheme.colors.borderInteractiveSecondary, shape)
            .animateContentSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.clariva_auth_consultant_title),
                    style = ElementTheme.typography.fontBodyMdMedium,
                    color = ElementTheme.colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.clariva_auth_consultant_subtitle),
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
            Switch(
                checked = state.isConsultant,
                enabled = !state.isLoading,
                onCheckedChange = { eventSink(ClarivaAuthEvents.SetIsConsultant(it)) },
                modifier = Modifier.testTag("clariva-auth-consultant"),
            )
        }

        AnimatedVisibility(
            visible = state.isConsultant,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                if (state.consultantSetup.isSkipped) {
                    SkippedSetup(
                        enabled = !state.isLoading,
                        onResume = { eventSink(ClarivaAuthEvents.ResumeConsultantSetup) },
                    )
                } else {
                    ConsultantSetupFields(
                        setup = state.consultantSetup,
                        enabled = !state.isLoading,
                        holderNameHint = state.name,
                        eventSink = state.eventSink,
                        firstFieldFocusRequester = firstFieldFocusRequester,
                        onDone = onSubmit,
                        onSkip = { state.eventSink(ClarivaAuthEvents.SkipConsultantSetup) },
                    )
                }
            }
        }
    }
}

/**
 * The optional chat rate + bank account fields. Shared by the sign-up card and
 * the Google sign-up payout step, so both ask for, and check, exactly the same.
 *
 * [onSkip] null hides the inline "Skip for now" (the Google step has its own).
 */
@Composable
internal fun ConsultantSetupFields(
    setup: ConsultantSetupForm,
    enabled: Boolean,
    holderNameHint: String?,
    eventSink: (ClarivaAuthEvents) -> Unit,
    onDone: () -> Unit,
    onSkip: (() -> Unit)?,
    modifier: Modifier = Modifier,
    firstFieldFocusRequester: FocusRequester = remember { FocusRequester() },
    showHeader: Boolean = true,
) {
    val focusManager = LocalFocusManager.current
    fun set(field: ConsultantSetupField, value: String) =
        eventSink(ClarivaAuthEvents.SetConsultantSetupField(field, value.replace("\n", "")))
    val next = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })

    Column(modifier = modifier.fillMaxWidth()) {
        if (showHeader) {
            SetupHeader()
            Spacer(Modifier.height(16.dp))
        }
        TextField(
            label = stringResource(R.string.clariva_signup_max_credits_label),
            value = setup.maxCredits,
            enabled = enabled,
            // Rejecting a keystroke keeps the IME in sync; rewriting the text would not.
            onValueChange = {
                if (it.all(Char::isDigit) && it.length <= ConsultantSetupForm.MAX_CREDITS_MAX_DIGITS) {
                    set(ConsultantSetupField.MaxCredits, it)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(firstFieldFocusRequester)
                .testTag("clariva-signup-max-credits"),
            placeholder = stringResource(R.string.clariva_signup_max_credits_placeholder),
            trailingIcon = {
                Text(
                    text = stringResource(R.string.clariva_signup_max_credits_suffix),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                    modifier = Modifier.padding(end = 12.dp),
                )
            },
            supportingText = setup.maxCreditsError ?: stringResource(R.string.clariva_signup_max_credits_help),
            validity = if (setup.maxCreditsError != null) TextFieldValidity.Invalid else TextFieldValidity.None,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            keyboardActions = next,
            singleLine = true,
        )

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = CompoundIcons.LockSolid(),
                contentDescription = null,
                tint = ElementTheme.colors.iconSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.clariva_signup_bank_title),
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textPrimary,
            )
        }
        Text(
            text = stringResource(R.string.clariva_signup_bank_help),
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
        )

        Spacer(Modifier.height(12.dp))
        TextField(
            label = stringResource(R.string.clariva_signup_account_holder_label),
            value = setup.accountHolderName,
            enabled = enabled,
            onValueChange = {
                if (it.length <= ConsultantSetupForm.MAX_TEXT_LENGTH) set(ConsultantSetupField.AccountHolderName, it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("clariva-signup-account-holder"),
            placeholder = holderNameHint?.trim()?.takeIf { it.isNotEmpty() },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            keyboardActions = next,
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        TextField(
            label = stringResource(R.string.clariva_signup_bank_name_label),
            value = setup.bankName,
            enabled = enabled,
            onValueChange = {
                if (it.length <= ConsultantSetupForm.MAX_TEXT_LENGTH) set(ConsultantSetupField.BankName, it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("clariva-signup-bank-name"),
            placeholder = stringResource(R.string.clariva_signup_bank_name_placeholder),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            keyboardActions = next,
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        TextField(
            label = stringResource(R.string.clariva_signup_account_number_label),
            value = setup.accountNumber,
            enabled = enabled,
            onValueChange = {
                if (it.all(Char::isDigit) && it.length <= ConsultantSetupForm.ACCOUNT_NUMBER_MAX_DIGITS) {
                    set(ConsultantSetupField.AccountNumber, it)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("clariva-signup-account-number"),
            supportingText = setup.accountNumberError,
            validity = if (setup.accountNumberError != null) TextFieldValidity.Invalid else TextFieldValidity.None,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            keyboardActions = next,
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        TextField(
            label = stringResource(R.string.clariva_signup_ifsc_label),
            value = setup.ifscCode,
            enabled = enabled,
            // Kept as typed (the keyboard capitalises); upper-cased when checked and saved.
            onValueChange = {
                if (it.all(Char::isLetterOrDigit) && it.length <= ConsultantSetupForm.IFSC_LENGTH) {
                    set(ConsultantSetupField.IfscCode, it)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("clariva-signup-ifsc"),
            placeholder = stringResource(R.string.clariva_signup_ifsc_placeholder),
            supportingText = setup.ifscError,
            validity = if (setup.ifscError != null) TextFieldValidity.Invalid else TextFieldValidity.None,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = next,
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        TextField(
            label = stringResource(R.string.clariva_signup_branch_label),
            value = setup.branchName,
            enabled = enabled,
            onValueChange = {
                if (it.length <= ConsultantSetupForm.MAX_TEXT_LENGTH) set(ConsultantSetupField.BranchName, it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("clariva-signup-branch"),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onDone()
            }),
            singleLine = true,
        )

        // "Create account" stays disabled while a bank account is half typed, so say why.
        if (setup.isBankDetailsIncomplete) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = CompoundIcons.Info(),
                    contentDescription = null,
                    tint = ElementTheme.colors.iconCriticalPrimary,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.clariva_signup_bank_incomplete),
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textCriticalPrimary,
                )
            }
        }

        if (onSkip != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                text = stringResource(R.string.clariva_signup_setup_skip),
                onClick = {
                    focusManager.clearFocus()
                    onSkip()
                },
                enabled = enabled,
                size = ButtonSize.Medium,
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag("clariva-signup-setup-skip"),
            )
        }
    }
}

/** "Set up payouts  [Optional]" and the line under it. */
@Composable
internal fun SetupHeader(subtitle: String = stringResource(R.string.clariva_signup_setup_subtitle)) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.clariva_signup_setup_title),
                style = ElementTheme.typography.fontBodyLgMedium,
                color = ElementTheme.colors.textPrimary,
            )
            Spacer(Modifier.width(8.dp))
            OptionalTag()
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun SkippedSetup(
    enabled: Boolean,
    onResume: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = CompoundIcons.Info(),
                contentDescription = null,
                tint = ElementTheme.colors.iconSecondary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.clariva_signup_setup_skipped),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
        TextButton(
            text = stringResource(R.string.clariva_signup_setup_resume),
            onClick = onResume,
            enabled = enabled,
            size = ButtonSize.Medium,
            modifier = Modifier
                .align(Alignment.End)
                .testTag("clariva-signup-setup-resume"),
        )
    }
}

@Composable
private fun OptionalTag() {
    Text(
        text = stringResource(R.string.clariva_signup_setup_optional),
        style = ElementTheme.typography.fontBodyXsMedium,
        color = ElementTheme.colors.textSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(ElementTheme.colors.bgSubtleSecondary)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
