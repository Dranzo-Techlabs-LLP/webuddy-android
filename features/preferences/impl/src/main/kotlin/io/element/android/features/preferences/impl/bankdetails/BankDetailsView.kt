/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.bankdetails

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.ButtonSize
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TextFieldValidity
import io.element.android.libraries.designsystem.utils.snackbar.LocalSnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarHost
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.designsystem.utils.snackbar.collectSnackbarMessageAsState
import io.element.android.libraries.designsystem.utils.snackbar.rememberSnackbarHostState
import io.element.android.libraries.network.wallet.BankAccountRules
import io.element.android.libraries.network.wallet.IfscBranch
import io.element.android.libraries.network.wallet.IfscStatus
import io.element.android.libraries.network.wallet.WalletApiException
import io.element.android.libraries.network.wallet.SavedBankAccount
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun BankDetailsView(
    state: BankDetailsState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarDispatcher = LocalSnackbarDispatcher.current
    val snackbarMessage by snackbarDispatcher.collectSnackbarMessageAsState()
    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = snackbarMessage)
    val focusManager = LocalFocusManager.current
    val isSaving = state.saveAction.isLoading()

    LaunchedEffect(state.saveAction) {
        if (state.saveAction is AsyncAction.Success) {
            snackbarDispatcher.post(SnackbarMessage(CommonStrings.common_saved_changes))
            onBackClick()
        }
    }

    fun save() {
        focusManager.clearFocus()
        if (state.canSave) state.eventSink(BankDetailsEvents.Save)
    }
    val next = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })

    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = "Bank Details",
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            SavedAccountCard(
                savedAccount = state.savedAccount,
                onRetry = { state.eventSink(BankDetailsEvents.RetryLoad) },
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = if (state.savedAccount.dataOrNull() != null) "Replace payout account" else "Add payout account",
                style = ElementTheme.typography.fontBodyLgMedium,
                color = ElementTheme.colors.textPrimary,
            )
            Text(
                text = "Your earnings are paid into this account.",
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = state.accountHolderName,
                onValueChange = {
                    if (it.length <= BankAccountRules.MAX_TEXT_LENGTH) {
                        state.eventSink(BankDetailsEvents.SetAccountHolderName(it.replace("\n", "")))
                    }
                },
                label = "Account Holder Name",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bank-details-holder"),
                singleLine = true,
                enabled = !isSaving,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                keyboardActions = next,
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = state.bankName,
                onValueChange = {
                    if (it.length <= BankAccountRules.MAX_TEXT_LENGTH) {
                        state.eventSink(BankDetailsEvents.SetBankName(it.replace("\n", "")))
                    }
                },
                label = "Bank Name",
                placeholder = "e.g. State Bank of India",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bank-details-bank-name"),
                singleLine = true,
                enabled = !isSaving,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                keyboardActions = next,
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = state.accountNumber,
                // Rejecting a keystroke keeps the IME in sync; rewriting the text would not.
                onValueChange = {
                    if (it.all(Char::isDigit) && it.length <= BankAccountRules.ACCOUNT_NUMBER_MAX_DIGITS) {
                        state.eventSink(BankDetailsEvents.SetAccountNumber(it))
                    }
                },
                label = "Account Number",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bank-details-account-number"),
                singleLine = true,
                enabled = !isSaving,
                supportingText = state.accountNumberError,
                validity = if (state.accountNumberError != null) TextFieldValidity.Invalid else TextFieldValidity.None,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                keyboardActions = next,
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = state.ifscCode,
                onValueChange = {
                    if (it.all(Char::isLetterOrDigit) && it.length <= BankAccountRules.IFSC_LENGTH) {
                        state.eventSink(BankDetailsEvents.SetIfscCode(it))
                    }
                },
                label = "IFSC Code",
                placeholder = "e.g. SBIN0001234",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bank-details-ifsc"),
                singleLine = true,
                enabled = !isSaving,
                supportingText = state.ifscError ?: ifscStatusText(state.ifscStatus),
                validity = when {
                    state.ifscError != null || state.ifscStatus is IfscStatus.NotFound -> TextFieldValidity.Invalid
                    state.ifscStatus is IfscStatus.Found -> TextFieldValidity.Valid
                    else -> TextFieldValidity.None
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = next,
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = state.branchName,
                onValueChange = {
                    if (it.length <= BankAccountRules.MAX_TEXT_LENGTH) {
                        state.eventSink(BankDetailsEvents.SetBranchName(it.replace("\n", "")))
                    }
                },
                label = "Branch Name (optional)",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bank-details-branch"),
                singleLine = true,
                enabled = !isSaving,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )

            val failure = state.saveAction as? AsyncAction.Failure
            if (failure != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = CompoundIcons.Error(),
                        contentDescription = null,
                        tint = ElementTheme.colors.iconCriticalPrimary,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        // The API's own reason for a rejected save (e.g. an unknown IFSC), else a generic one.
                        text = (failure.error as? WalletApiException)?.userMessage
                            ?: "Couldn't save your bank details. Check your connection and try again.",
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = ElementTheme.colors.textCriticalPrimary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                text = "Save Details",
                onClick = ::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bank-details-save"),
                showProgress = isSaving,
                enabled = state.canSave,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Saving replaces your current payout account. Only the last 4 digits of the account number are shown here.",
                style = ElementTheme.typography.fontBodyXsRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
    }
}

/** What the IFSC lookup found, shown under the field. */
private fun ifscStatusText(status: IfscStatus): String? = when (status) {
    IfscStatus.None -> null
    IfscStatus.Checking -> "Checking IFSC…"
    is IfscStatus.Found -> status.branch.summary
    IfscStatus.NotFound -> "No bank branch found for this IFSC. Please check the code."
    IfscStatus.Unavailable -> "Couldn't verify this IFSC right now. It will be checked when you save."
}

@Composable
private fun SavedAccountCard(
    savedAccount: AsyncData<SavedBankAccount?>,
    onRetry: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ElementTheme.colors.bgSubtleSecondary)
            .border(1.dp, ElementTheme.colors.borderInteractiveSecondary, shape)
            .padding(16.dp)
            .testTag("bank-details-saved"),
    ) {
        Text(
            text = "Current payout account",
            style = ElementTheme.typography.fontBodySmMedium,
            color = ElementTheme.colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        when (savedAccount) {
            is AsyncData.Success -> {
                val account = savedAccount.data
                if (account == null) {
                    Text(
                        text = "None yet. Add one below to receive your earnings.",
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textPrimary,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = CompoundIcons.LockSolid(),
                            contentDescription = null,
                            tint = ElementTheme.colors.iconSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${account.bankName} •••• ${account.accountNumberLast4}",
                            style = ElementTheme.typography.fontBodyLgMedium,
                            color = ElementTheme.colors.textPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = listOfNotNull(
                            account.accountHolderName,
                            "IFSC ${account.ifscCode}",
                            account.branchName?.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                }
            }
            is AsyncData.Failure -> {
                Text(
                    text = "Couldn't load your saved account.",
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textCriticalPrimary,
                )
                TextButton(text = "Retry", onClick = onRetry, size = ButtonSize.Small)
            }
            else -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

internal class BankDetailsStateProvider : PreviewParameterProvider<BankDetailsState> {
    override val values: Sequence<BankDetailsState>
        get() = sequenceOf(
            aBankDetailsState(),
            aBankDetailsState(
                savedAccount = AsyncData.Success(
                    SavedBankAccount(
                        bankName = "State Bank of India",
                        accountNumberLast4 = "9012",
                        ifscCode = "SBIN0001234",
                        accountHolderName = "Jane Doe",
                        branchName = "MG Road",
                    )
                ),
                accountNumber = "1234",
                ifscCode = "sbin01",
            ),
            aBankDetailsState(
                ifscCode = "SBIN0001234",
                ifscStatus = IfscStatus.Found(IfscBranch(bank = "State Bank of India", branch = "Hajiganj", city = "Patna")),
            ),
            aBankDetailsState(ifscCode = "ABCD0123456", ifscStatus = IfscStatus.NotFound),
            aBankDetailsState(savedAccount = AsyncData.Failure(Exception("offline")), saveAction = AsyncAction.Failure(Exception("offline"))),
        )
}

private fun aBankDetailsState(
    savedAccount: AsyncData<SavedBankAccount?> = AsyncData.Success(null),
    accountNumber: String = "",
    ifscCode: String = "",
    ifscStatus: IfscStatus = IfscStatus.None,
    saveAction: AsyncAction<Unit> = AsyncAction.Uninitialized,
) = BankDetailsState(
    savedAccount = savedAccount,
    bankName = "",
    accountNumber = accountNumber,
    ifscCode = ifscCode,
    accountHolderName = "",
    branchName = "",
    ifscStatus = ifscStatus,
    saveAction = saveAction,
    eventSink = {},
)

@PreviewsDayNight
@Composable
internal fun BankDetailsViewPreview(@PreviewParameter(BankDetailsStateProvider::class) state: BankDetailsState) = ElementPreview {
    BankDetailsView(state = state, onBackClick = {})
}
