/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.clarivaauth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.login.impl.R
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextField

/**
 * Two-step "forgot password" dialog: request a 6-digit code by email, then enter
 * the code with a new password. Kept as a plain [Dialog] (not one of the design
 * system's single-purpose dialogs) because it holds a small multi-field form.
 *
 * The server never reveals whether an email is registered, so this UI does not
 * either: after "Send code" it always advances to the code step regardless.
 */
@Composable
fun ClarivaForgotPasswordDialog(
    state: ForgotPasswordState,
    eventSink: (ClarivaAuthEvents) -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = { eventSink(ClarivaAuthEvents.ForgotPasswordDismiss) }) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = ElementTheme.colors.bgCanvasDefault,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.clariva_forgot_title),
                    style = ElementTheme.typography.fontHeadingSmMedium,
                    color = ElementTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(12.dp))

                when {
                    state.done -> DoneStep(eventSink)
                    state.step == ForgotPasswordStep.EnterEmail -> EnterEmailStep(state, eventSink)
                    else -> EnterCodeStep(state, eventSink)
                }
            }
        }
    }
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = error,
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textCriticalPrimary,
        )
    }
}

@Composable
private fun DoneStep(eventSink: (ClarivaAuthEvents) -> Unit) {
    Text(
        text = stringResource(R.string.clariva_forgot_success),
        style = ElementTheme.typography.fontBodyMdRegular,
        color = ElementTheme.colors.textPrimary,
    )
    Spacer(Modifier.height(20.dp))
    Button(
        text = stringResource(R.string.clariva_auth_action_sign_in),
        onClick = { eventSink(ClarivaAuthEvents.ForgotPasswordDismiss) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EnterEmailStep(
    state: ForgotPasswordState,
    eventSink: (ClarivaAuthEvents) -> Unit,
) {
    Text(
        text = stringResource(R.string.clariva_forgot_email_subtitle),
        style = ElementTheme.typography.fontBodyMdRegular,
        color = ElementTheme.colors.textSecondary,
    )
    Spacer(Modifier.height(16.dp))
    TextField(
        label = stringResource(R.string.clariva_auth_email_label),
        value = state.email,
        enabled = !state.isLoading,
        onValueChange = { eventSink(ClarivaAuthEvents.ForgotPasswordSetEmail(it)) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = stringResource(R.string.clariva_auth_email_placeholder),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { eventSink(ClarivaAuthEvents.ForgotPasswordRequestCode) }),
        singleLine = true,
    )
    ErrorText(state.error)
    Spacer(Modifier.height(20.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            text = stringResource(R.string.clariva_forgot_cancel),
            onClick = { eventSink(ClarivaAuthEvents.ForgotPasswordDismiss) },
            enabled = !state.isLoading,
        )
        Spacer(Modifier.width(12.dp))
        Button(
            text = stringResource(R.string.clariva_forgot_send_code),
            showProgress = state.isLoading,
            enabled = state.sendEnabled,
            onClick = { eventSink(ClarivaAuthEvents.ForgotPasswordRequestCode) },
        )
    }
}

@Composable
private fun EnterCodeStep(
    state: ForgotPasswordState,
    eventSink: (ClarivaAuthEvents) -> Unit,
) {
    Text(
        text = stringResource(R.string.clariva_forgot_code_subtitle, state.email),
        style = ElementTheme.typography.fontBodyMdRegular,
        color = ElementTheme.colors.textSecondary,
    )
    Spacer(Modifier.height(16.dp))
    TextField(
        label = stringResource(R.string.clariva_forgot_code_label),
        value = state.code,
        enabled = !state.isLoading,
        onValueChange = { eventSink(ClarivaAuthEvents.ForgotPasswordSetCode(it)) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
        singleLine = true,
    )
    Spacer(Modifier.height(12.dp))
    var passwordVisible by remember { mutableStateOf(false) }
    if (state.isLoading) passwordVisible = false
    TextField(
        label = stringResource(R.string.clariva_forgot_new_password_label),
        value = state.newPassword,
        enabled = !state.isLoading,
        onValueChange = { eventSink(ClarivaAuthEvents.ForgotPasswordSetNewPassword(it)) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = stringResource(R.string.clariva_auth_password_hint, ClarivaAuthState.MIN_PASSWORD_LENGTH),
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            Box(Modifier.clickable { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) CompoundIcons.VisibilityOn() else CompoundIcons.VisibilityOff(),
                    contentDescription = if (passwordVisible) {
                        stringResource(R.string.clariva_auth_hide_password)
                    } else {
                        stringResource(R.string.clariva_auth_show_password)
                    },
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { eventSink(ClarivaAuthEvents.ForgotPasswordSubmit) }),
        singleLine = true,
    )
    ErrorText(state.error)
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.clariva_forgot_resend),
        style = ElementTheme.typography.fontBodySmMedium,
        color = ElementTheme.colors.textPrimary,
        modifier = Modifier
            .clickable(enabled = !state.isLoading) { eventSink(ClarivaAuthEvents.ForgotPasswordRequestCode) }
            .padding(vertical = 4.dp),
    )
    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            text = stringResource(R.string.clariva_forgot_cancel),
            onClick = { eventSink(ClarivaAuthEvents.ForgotPasswordDismiss) },
            enabled = !state.isLoading,
        )
        Spacer(Modifier.width(12.dp))
        Button(
            text = stringResource(R.string.clariva_forgot_submit),
            showProgress = state.isLoading,
            enabled = state.resetEnabled,
            onClick = { eventSink(ClarivaAuthEvents.ForgotPasswordSubmit) },
        )
    }
}

internal class ForgotPasswordStateProvider : PreviewParameterProvider<ForgotPasswordState> {
    override val values: Sequence<ForgotPasswordState>
        get() = sequenceOf(
            ForgotPasswordState(
                email = "jane@example.com",
                code = "",
                newPassword = "",
                step = ForgotPasswordStep.EnterEmail,
                isLoading = false,
                error = null,
            ),
            ForgotPasswordState(
                email = "jane@example.com",
                code = "123456",
                newPassword = "a-good-password",
                step = ForgotPasswordStep.EnterCode,
                isLoading = false,
                error = null,
            ),
            ForgotPasswordState(
                email = "jane@example.com",
                code = "000000",
                newPassword = "a-good-password",
                step = ForgotPasswordStep.EnterCode,
                isLoading = false,
                error = "That code is invalid or has expired. Please request a new one.",
            ),
            ForgotPasswordState(
                email = "jane@example.com",
                code = "123456",
                newPassword = "a-good-password",
                step = ForgotPasswordStep.EnterCode,
                isLoading = false,
                error = null,
                done = true,
            ),
        )
}

@PreviewsDayNight
@Composable
internal fun ClarivaForgotPasswordDialogPreview(
    @PreviewParameter(ForgotPasswordStateProvider::class) state: ForgotPasswordState,
) = ElementPreview {
    ClarivaForgotPasswordDialog(
        state = state,
        eventSink = {},
    )
}
