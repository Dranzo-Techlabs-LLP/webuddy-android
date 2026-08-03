/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.dialogs

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.element.android.libraries.designsystem.components.list.RadioButtonListItem
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewGroup
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.DialogPreview
import io.element.android.libraries.designsystem.theme.components.ListSupportingText
import io.element.android.libraries.designsystem.theme.components.SimpleAlertDialogContent
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleSelectionDialog(
    options: ImmutableList<ListOption>,
    onSelectOption: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    dismissButtonTitle: String = stringResource(CommonStrings.action_cancel),
    initialSelection: Int? = null,
    // Defaults preserve prior behaviour; callers that must not be dismissed by an
    // outside tap / back press (e.g. a choice that creates an account) can override.
    properties: DialogProperties = DialogProperties(),
    /**
     * When set, tapping a row only MOVES the selection and this button commits it,
     * so [initialSelection] acts as a real default the user can simply accept.
     * Left null, a tap commits immediately - the original behaviour.
     */
    confirmButtonTitle: String? = null,
) {
    val decoratedSubtitle: @Composable (() -> Unit)? = subtitle?.let {
        @Composable {
            ListSupportingText(
                text = it,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
    // Only used in confirm mode; harmless otherwise.
    var stagedSelection by rememberSaveable { mutableStateOf(initialSelection) }
    BasicAlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        if (confirmButtonTitle == null) {
            SingleSelectionDialogContent(
                title = title,
                subtitle = decoratedSubtitle,
                options = options,
                onOptionClick = onSelectOption,
                dismissButtonTitle = dismissButtonTitle,
                onDismissRequest = onDismissRequest,
                initialSelection = initialSelection,
            )
        } else {
            SimpleAlertDialogContent(
                title = title,
                subtitle = decoratedSubtitle,
                submitText = confirmButtonTitle,
                onSubmitClick = { stagedSelection?.let(onSelectOption) },
                enabled = stagedSelection != null,
                cancelText = dismissButtonTitle,
                onCancelClick = onDismissRequest,
                applyPaddingToContents = false,
            ) {
                LazyColumn {
                    itemsIndexed(options) { index, option ->
                        RadioButtonListItem(
                            headline = option.title,
                            supportingText = option.subtitle,
                            selected = index == stagedSelection,
                            onSelect = { stagedSelection = index },
                            compactLayout = true,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleSelectionDialogContent(
    options: ImmutableList<ListOption>,
    onOptionClick: (Int) -> Unit,
    dismissButtonTitle: String,
    onDismissRequest: () -> Unit,
    title: String? = null,
    initialSelection: Int? = null,
    subtitle: @Composable (() -> Unit)? = null,
) {
    SimpleAlertDialogContent(
        title = title,
        subtitle = subtitle,
        submitText = dismissButtonTitle,
        onSubmitClick = onDismissRequest,
        applyPaddingToContents = false,
    ) {
        LazyColumn {
            itemsIndexed(options) { index, option ->
                RadioButtonListItem(
                    headline = option.title,
                    supportingText = option.subtitle,
                    selected = index == initialSelection,
                    onSelect = { onOptionClick(index) },
                    compactLayout = true,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Preview(group = PreviewGroup.Dialogs)
@Composable
internal fun SingleSelectionDialogContentPreview() {
    ElementPreview(showBackground = false) {
        DialogPreview {
            val options = persistentListOf(
                ListOption("Option 1"),
                ListOption("Option 2"),
                ListOption("Option 3"),
            )
            SingleSelectionDialogContent(
                title = "Dialog title",
                options = options,
                onOptionClick = {},
                onDismissRequest = {},
                dismissButtonTitle = "Cancel",
                initialSelection = 0
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun SingleSelectionDialogPreview() = ElementPreview {
    val options = persistentListOf(
        ListOption("Option 1"),
        ListOption("Option 2"),
        ListOption("Option 3"),
    )
    SingleSelectionDialog(
        title = "Dialog title",
        options = options,
        onSelectOption = {},
        onDismissRequest = {},
        dismissButtonTitle = "Cancel",
        initialSelection = 0
    )
}
