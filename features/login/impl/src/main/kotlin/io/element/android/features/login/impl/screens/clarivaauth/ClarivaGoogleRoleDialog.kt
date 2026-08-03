/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.clarivaauth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.login.impl.R
import io.element.android.libraries.designsystem.components.dialogs.ListOption
import io.element.android.libraries.designsystem.components.dialogs.SingleSelectionDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import kotlinx.collections.immutable.persistentListOf

/** Index of each answer in the dialog's option list. "No" is the default. */
private const val OPTION_NO = 0
private const val OPTION_YES = 1

/**
 * Asks "Are you a consultant?" when a Google identity's role is not yet known.
 *
 * Google sign-in carries no form to hold the role, so without this every Google
 * user would silently become a normal client with no chance to opt in. Shown
 * only when the role is genuinely unknown - a returning user whose role is
 * already recorded is never asked, and the email sign-up form has its own
 * "I am a consultant" toggle instead.
 */
@Composable
fun ClarivaGoogleRoleDialog(
    prompt: GoogleRolePrompt,
    onSelect: (isConsultant: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleSelectionDialog(
        modifier = modifier,
        title = stringResource(R.string.clariva_google_role_title),
        subtitle = prompt.email?.let {
            stringResource(R.string.clariva_google_role_subtitle_with_email, it)
        } ?: stringResource(R.string.clariva_google_role_subtitle),
        options = persistentListOf(
            ListOption(
                title = stringResource(R.string.clariva_google_role_no),
                subtitle = stringResource(R.string.clariva_google_role_no_hint),
            ),
            ListOption(
                title = stringResource(R.string.clariva_google_role_yes),
                subtitle = stringResource(R.string.clariva_google_role_yes_hint),
            ),
        ),
        onSelectOption = { index -> onSelect(index == OPTION_YES) },
        onDismissRequest = onDismiss,
        // "No" pre-selected, and a confirm button so accepting the default is a
        // single tap rather than a choice the user must actively re-make.
        initialSelection = OPTION_NO,
        confirmButtonTitle = stringResource(R.string.clariva_google_role_confirm),
        // The Google account-picker sheet has already run by this point; an
        // outside-tap or back-press dismiss would silently discard the sign-up.
        // Force an explicit choice (there is still a Cancel button in the dialog).
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    )
}

internal class GoogleRolePromptProvider : PreviewParameterProvider<GoogleRolePrompt> {
    override val values: Sequence<GoogleRolePrompt>
        get() = sequenceOf(
            GoogleRolePrompt(idToken = "tok", email = "jane@example.com", name = "Jane Doe"),
            GoogleRolePrompt(idToken = "tok", email = null, name = null),
            GoogleRolePrompt(idToken = "tok", email = "bob@example.com", name = "Bob", isExisting = true),
        )
}

@PreviewsDayNight
@Composable
internal fun ClarivaGoogleRoleDialogPreview(
    @PreviewParameter(GoogleRolePromptProvider::class) prompt: GoogleRolePrompt,
) = ElementPreview {
    ClarivaGoogleRoleDialog(
        prompt = prompt,
        onSelect = {},
        onDismiss = {},
    )
}
