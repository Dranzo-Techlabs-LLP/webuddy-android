/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.clarivaauth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.login.impl.R
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.atomic.atoms.ElementLogoAtom
import io.element.android.libraries.designsystem.atomic.atoms.ElementLogoAtomSize
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Switch
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TextFieldValidity

/**
 * Clariva landing screen: this IS the app's welcome page.
 *
 * The Clariva logo and welcome message sit at the top exactly as on the previous
 * onboarding screen, and the sign-in fields follow directly beneath them, so a
 * returning user can sign in without an extra tap. The whole column scrolls and
 * respects the IME so the form stays reachable on short screens.
 */
@Composable
fun ClarivaAuthView(
    state: ClarivaAuthState,
    onBackClick: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val isSignUp = state.mode == ClarivaAuthMode.SignUp

    fun submit() {
        focusManager.clearFocus(force = true)
        state.eventSink(ClarivaAuthEvents.Submit)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(clarivaBrandBrush(isLight = ElementTheme.isLightTheme))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
                // heightIn(min = viewport) + Arrangement.Center gives a balanced,
                // vertically centred page when the content is short (sign-in), and
                // still scrolls normally when it is not (sign-up, or keyboard open).
                // A plain scrolling Column would top-align and leave dead space.
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                ) {
                    val viewportHeight = maxHeight
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = viewportHeight),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                    Spacer(Modifier.height(16.dp))

                    // --- Branding: identical logo + copy to the old welcome page ---
                    ElementLogoAtom(size = ElementLogoAtomSize.Medium)

                    Spacer(Modifier.height(28.dp))

                    Text(
                        text = stringResource(id = R.string.screen_onboarding_welcome_title),
                        color = ElementTheme.colors.textPrimary,
                        style = ElementTheme.typography.fontHeadingLgBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.clariva_welcome_tagline),
                        color = ElementTheme.colors.textSecondary,
                        style = ElementTheme.typography.fontBodyLgRegular.copy(fontSize = 17.sp),
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(32.dp))

                    // --- Sign-in / sign-up fields, directly below the welcome note ---
                    ClarivaAuthForm(
                        state = state,
                        isSignUp = isSignUp,
                        onSubmit = ::submit,
                    )

                    Spacer(Modifier.height(24.dp))

                    Button(
                        text = if (isSignUp) {
                            stringResource(R.string.clariva_auth_action_create_account)
                        } else {
                            stringResource(R.string.clariva_auth_action_sign_in)
                        },
                        showProgress = state.isLoading,
                        onClick = ::submit,
                        enabled = state.submitEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clariva-auth-submit"),
                    )

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.clariva_auth_or),
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                    Spacer(Modifier.height(16.dp))

                    OutlinedButton(
                        text = if (isSignUp) {
                            stringResource(R.string.clariva_auth_action_sign_up_google)
                        } else {
                            stringResource(R.string.clariva_auth_action_sign_in_google)
                        },
                        onClick = onGoogleSignInClick,
                        enabled = !state.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clariva-auth-google"),
                    )

                    if (!state.isGoogleSignInConfigured) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.clariva_auth_google_not_configured),
                            style = ElementTheme.typography.fontBodyXsRegular,
                            color = ElementTheme.colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    Text(
                        modifier = Modifier
                            .clickable(enabled = !state.isLoading) {
                                state.eventSink(ClarivaAuthEvents.ToggleMode)
                            }
                            .padding(8.dp)
                            .testTag("clariva-auth-toggle-mode"),
                        text = if (isSignUp) {
                            stringResource(R.string.clariva_auth_have_account)
                        } else {
                            stringResource(R.string.clariva_auth_no_account)
                        },
                        style = ElementTheme.typography.fontBodyMdMedium,
                        color = ElementTheme.colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(20.dp))

                    // Carried over from the old welcome page - useful for support.
                    Text(
                        text = stringResource(id = R.string.screen_onboarding_app_version, state.version),
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = ElementTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(8.dp))
                    }
                }
        }

        // Only reachable when adding a second account - as the app's root screen
        // there is nothing to go back to, matching the previous welcome page.
        if (state.isAddingAccount) {
            BackButton(
                onClick = onBackClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .systemBarsPadding()
                    .padding(4.dp),
            )
        }

        // New Google users pick a role before the account is created. Not
        // dismissible by tapping outside: an accidental dismiss would silently
        // abandon a sign-up that has already been through the Google sheet.
        state.googleRolePrompt?.let { prompt ->
            ClarivaGoogleRoleDialog(
                prompt = prompt,
                onSelect = { state.eventSink(ClarivaAuthEvents.ConfirmGoogleRole(it)) },
                onDismiss = { state.eventSink(ClarivaAuthEvents.CancelGoogleRole) },
            )
        }

        state.forgotPassword?.let { fp ->
            ClarivaForgotPasswordDialog(
                state = fp,
                eventSink = state.eventSink,
            )
        }

        val failure = state.authAction
        if (failure is AsyncData.Failure) {
            ErrorDialog(
                title = stringResource(R.string.clariva_auth_error_title),
                content = failure.error.message
                    ?: stringResource(R.string.clariva_auth_error_generic),
                onSubmit = { state.eventSink(ClarivaAuthEvents.ClearError) },
            )
        }
    }
}

@Composable
private fun ClarivaAuthForm(
    state: ClarivaAuthState,
    isSignUp: Boolean,
    onSubmit: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val eventSink = state.eventSink

    Column(modifier = Modifier.fillMaxWidth()) {
        if (isSignUp) {
            TextField(
                label = stringResource(R.string.clariva_auth_name_label),
                value = state.name,
                enabled = !state.isLoading,
                onValueChange = { eventSink(ClarivaAuthEvents.SetName(it.sanitize())) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("clariva-auth-name"),
                placeholder = stringResource(R.string.clariva_auth_name_placeholder),
                supportingText = state.nameError,
                validity = if (state.nameError != null) TextFieldValidity.Invalid else TextFieldValidity.None,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))

            TextField(
                label = stringResource(R.string.clariva_auth_username_label),
                value = state.username,
                enabled = !state.isLoading,
                onValueChange = { eventSink(ClarivaAuthEvents.SetUsername(it.sanitize())) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("clariva-auth-username"),
                placeholder = stringResource(R.string.clariva_auth_username_placeholder),
                // Priority: hard error > live availability > MXID preview > help.
                // The preview matters because the handle is permanent.
                supportingText = state.usernameError ?: when (state.usernameAvailability) {
                    UsernameAvailability.Checking ->
                        stringResource(R.string.clariva_auth_username_checking)
                    UsernameAvailability.Available ->
                        stringResource(R.string.clariva_auth_username_available, state.normalizedUsername)
                    else -> if (state.username.isNotEmpty()) {
                        stringResource(R.string.clariva_auth_username_preview, state.normalizedUsername)
                    } else {
                        stringResource(R.string.clariva_auth_username_help)
                    }
                },
                validity = when {
                    state.usernameError != null -> TextFieldValidity.Invalid
                    state.usernameAvailability == UsernameAvailability.Available -> TextFieldValidity.Valid
                    else -> TextFieldValidity.None
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))
        }

        TextField(
            // Sign-in resolves an email OR a username; sign-up must be an email,
            // since that is what identifies the account afterwards.
            label = stringResource(
                if (isSignUp) R.string.clariva_auth_email_label else R.string.clariva_auth_identifier_label
            ),
            value = state.email,
            enabled = !state.isLoading,
            onValueChange = { eventSink(ClarivaAuthEvents.SetEmail(it.sanitize())) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("clariva-auth-email"),
            placeholder = stringResource(
                if (isSignUp) R.string.clariva_auth_email_placeholder else R.string.clariva_auth_identifier_placeholder
            ),
            // Live "already registered" feedback on sign-up, mirroring the username field.
            supportingText = if (isSignUp) {
                when (state.emailAvailability) {
                    EmailAvailability.Checking -> stringResource(R.string.clariva_auth_email_checking)
                    EmailAvailability.Available -> stringResource(R.string.clariva_auth_email_available)
                    EmailAvailability.Taken -> stringResource(R.string.clariva_auth_email_taken)
                    EmailAvailability.Unknown -> null
                }
            } else {
                null
            },
            validity = if (isSignUp) {
                when (state.emailAvailability) {
                    EmailAvailability.Taken -> TextFieldValidity.Invalid
                    EmailAvailability.Available -> TextFieldValidity.Valid
                    else -> TextFieldValidity.None
                }
            } else {
                TextFieldValidity.None
            },
            keyboardOptions = KeyboardOptions(
                // Plain text on sign-in: the email keyboard hides letters behind
                // an '@'-first layout, which is wrong for a username.
                keyboardType = if (isSignUp) KeyboardType.Email else KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            singleLine = true,
        )

        Spacer(Modifier.height(16.dp))

        var passwordVisible by remember { mutableStateOf(false) }
        if (state.isLoading) passwordVisible = false

        TextField(
            label = stringResource(R.string.clariva_auth_password_label),
            value = state.password,
            enabled = !state.isLoading,
            onValueChange = { eventSink(ClarivaAuthEvents.SetPassword(it.sanitize())) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("clariva-auth-password"),
            placeholder = if (isSignUp) {
                stringResource(R.string.clariva_auth_password_hint, ClarivaAuthState.MIN_PASSWORD_LENGTH)
            } else {
                null
            },
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
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            singleLine = true,
        )

        if (!isSignUp) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.clariva_auth_forgot_password),
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textPrimary,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable(enabled = !state.isLoading) {
                        eventSink(ClarivaAuthEvents.ForgotPasswordOpen)
                    }
                    .padding(4.dp)
                    .testTag("clariva-auth-forgot-password"),
            )
        }

        if (isSignUp) {
            Spacer(Modifier.height(20.dp))
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
        }
    }
}

/**
 * Background gradient built from the Clariva logo's own palette: the mint/teal
 * speech bubble and the deep navy one. Deliberately fades back to a pale tint at
 * the bottom so the footer text and the outlined Google button keep their
 * contrast, and stays light through the middle band where the white input
 * fields sit.
 */
private fun clarivaBrandBrush(isLight: Boolean): Brush = Brush.verticalGradient(
    // Stops are placed against the content zones rather than spread evenly:
    //   ~0.20 deeper teal  -> the glassmorphic logo tile (white fill + white
    //                         border) needs a darker field behind it or it
    //                         disappears into a pale background
    //   ~0.40 light        -> title and tagline sit here and must stay high
    //                         contrast
    //   ~0.62 mid teal     -> the white input fields pop against it
    //   1.00  light        -> footer text and the outlined Google button
    colorStops = if (isLight) {
        arrayOf(
            0.00f to Color(0xFFEAF8F3),
            0.20f to Color(0xFF74D2BC), // brand teal, from the logo bubble
            0.40f to Color(0xFFE2F6EF),
            0.62f to Color(0xFF9FE2D3),
            1.00f to Color(0xFFF4FBF9),
        )
    } else {
        arrayOf(
            0.00f to Color(0xFF0B1622), // deep navy, from the dark bubble
            0.20f to Color(0xFF0F5A50),
            0.40f to Color(0xFF0D1F2C),
            0.62f to Color(0xFF10463F),
            1.00f to Color(0xFF0A1420),
        )
    },
)

/** Strip newlines that arrive via paste. */
private fun String.sanitize(): String = replace("\n", "")

@PreviewsDayNight
@Composable
internal fun ClarivaAuthViewPreview(@PreviewParameter(ClarivaAuthStateProvider::class) state: ClarivaAuthState) = ElementPreview {
    ClarivaAuthView(
        state = state,
        onBackClick = {},
        onGoogleSignInClick = {},
    )
}
