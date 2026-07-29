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

/** Index of each role in the dialog's option list. */
private const val OPTION_CLIENT = 0
private const val OPTION_CONSULTANT = 1

/**
 * Asks a brand-new Google user whether they are a consultant.
 *
 * Google sign-in carries no form to hold the role, and this choice is what
 * creates the account - without it every Google user would silently become a
 * normal client with no chance to opt into being a consultant at sign-up.
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
                title = stringResource(R.string.clariva_google_role_client),
                subtitle = stringResource(R.string.clariva_google_role_client_hint),
            ),
            ListOption(
                title = stringResource(R.string.clariva_google_role_consultant),
                subtitle = stringResource(R.string.clariva_google_role_consultant_hint),
            ),
        ),
        onSelectOption = { index -> onSelect(index == OPTION_CONSULTANT) },
        onDismissRequest = onDismiss,
        initialSelection = OPTION_CLIENT,
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
