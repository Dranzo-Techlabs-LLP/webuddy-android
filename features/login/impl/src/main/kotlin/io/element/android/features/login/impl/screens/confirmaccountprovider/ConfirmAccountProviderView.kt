/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.confirmaccountprovider

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.login.impl.R
import io.element.android.features.login.impl.login.LoginModeView
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.atomic.molecules.ButtonColumnMolecule
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.atomic.pages.HeaderFooterPage
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.matrix.api.auth.OidcDetails
import io.element.android.libraries.testtags.TestTags
import io.element.android.libraries.testtags.testTag
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun ConfirmAccountProviderView(
    state: ConfirmAccountProviderState,
    onOidcDetails: (OidcDetails) -> Unit,
    onNeedLoginPassword: () -> Unit,
    onLearnMoreClick: () -> Unit,
    onCreateAccountContinue: (url: String) -> Unit,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoading by remember(state.loginMode) {
        derivedStateOf {
            state.loginMode is AsyncData.Loading
        }
    }
    val eventSink = state.eventSink

    HeaderFooterPage(
        modifier = modifier,
        header = {
            IconTitleSubtitleMolecule(
                modifier = Modifier.padding(top = 60.dp),
                iconStyle = BigIcon.Style.Default(CompoundIcons.UserProfileSolid()),
                title = stringResource(
                    id = if (state.isAccountCreation) {
                        R.string.screen_account_provider_signup_title
                    } else {
                        R.string.screen_account_provider_signin_title
                    },
                    state.accountProvider.title
                ),
                subTitle = stringResource(
                    id = if (state.isAccountCreation) {
                        R.string.screen_account_provider_signup_subtitle
                    } else {
                        R.string.screen_account_provider_signin_subtitle
                    },
                )
            )
        },
        footer = {
            ButtonColumnMolecule {
                Button(
                    text = stringResource(id = CommonStrings.action_continue),
                    showProgress = isLoading,
                    onClick = { eventSink.invoke(ConfirmAccountProviderEvents.Continue) },
                    enabled = state.submitEnabled || isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.loginContinue)
                )
                TextButton(
                    text = stringResource(id = R.string.screen_account_provider_change),
                    onClick = onChange,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.loginChangeServer)
                )
            }
        }
    ) {
        if (state.isAccountCreation) {
            RolePickerSection(
                isConsultant = state.isConsultant,
                onSelect = { eventSink(ConfirmAccountProviderEvents.SelectRole(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
            )
        }
        LoginModeView(
            loginMode = state.loginMode,
            onClearError = {
                eventSink(ConfirmAccountProviderEvents.ClearError)
            },
            onLearnMoreClick = onLearnMoreClick,
            onOidcDetails = onOidcDetails,
            onNeedLoginPassword = onNeedLoginPassword,
            onCreateAccountContinue = onCreateAccountContinue,
        )
    }
}

@Composable
private fun RolePickerSection(
    isConsultant: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "I'll use Clariva as",
            style = ElementTheme.typography.fontBodyLgMedium,
            color = ElementTheme.colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Normal users initiate paid chats with consultants. You can keep the default if you're not sure.",
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        RoleOption(
            label = "Normal User",
            description = "Initiate paid chats and recharge your wallet.",
            selected = !isConsultant,
            onClick = { onSelect(false) },
        )
        Spacer(modifier = Modifier.height(8.dp))
        RoleOption(
            label = "Consultant",
            description = "Receive paid chats from normal users and get holds released to you.",
            selected = isConsultant,
            onClick = { onSelect(true) },
        )
    }
}

@Composable
private fun RoleOption(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) ElementTheme.colors.borderInteractivePrimary else ElementTheme.colors.borderDisabled
    val bgColor = if (selected) ElementTheme.colors.bgSubtleSecondary else ElementTheme.colors.bgCanvasDefault
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column {
            Text(
                text = label,
                style = ElementTheme.typography.fontBodyLgMedium,
                color = ElementTheme.colors.textPrimary,
            )
            Text(
                text = description,
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun ConfirmAccountProviderViewPreview(
    @PreviewParameter(ConfirmAccountProviderStateProvider::class) state: ConfirmAccountProviderState
) = ElementPreview {
    ConfirmAccountProviderView(
        state = state,
        onOidcDetails = {},
        onNeedLoginPassword = {},
        onCreateAccountContinue = {},
        onLearnMoreClick = {},
        onChange = {},
    )
}
