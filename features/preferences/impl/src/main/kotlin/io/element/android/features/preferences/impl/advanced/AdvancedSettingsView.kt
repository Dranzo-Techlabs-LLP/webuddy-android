/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.advanced

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.element.android.libraries.architecture.AsyncAction
import im.vector.app.features.analytics.plan.Interaction
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.architecture.coverage.ExcludeFromCoverage
import io.element.android.libraries.designsystem.components.dialogs.ListDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.components.preferences.PreferenceDropdown
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.components.preferences.PreferenceSwitch
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.ElementPreviewDark
import io.element.android.libraries.designsystem.preview.ElementPreviewLight
import io.element.android.libraries.designsystem.preview.PreviewWithLargeHeight
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListSectionHeader
import io.element.android.libraries.designsystem.theme.components.ListSupportingText
import io.element.android.libraries.designsystem.theme.components.ListSupportingTextDefaults
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.snackbar.LocalSnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarHost
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.designsystem.utils.snackbar.collectSnackbarMessageAsState
import io.element.android.libraries.designsystem.utils.snackbar.rememberSnackbarHostState
import io.element.android.libraries.matrix.api.media.MediaPreviewValue
import io.element.android.libraries.preferences.api.store.VideoCompressionPreset
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.services.analytics.compose.LocalAnalyticsService
import io.element.android.services.analyticsproviders.api.trackers.captureInteraction
import kotlinx.collections.immutable.toImmutableList

@Composable
fun AdvancedSettingsView(
    state: AdvancedSettingsState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val analyticsService = LocalAnalyticsService.current

    val snackbarDispatcher = LocalSnackbarDispatcher.current
    val snackbarMessage by snackbarDispatcher.collectSnackbarMessageAsState()
    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = snackbarMessage)

    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = stringResource(id = CommonStrings.common_advanced_settings),
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) {
        PreferenceDropdown(
            title = stringResource(id = CommonStrings.common_appearance),
            selectedOption = state.theme,
            options = ThemeOption.entries.toImmutableList(),
            onSelectOption = { themeOption ->
                state.eventSink(AdvancedSettingsEvents.SetTheme(themeOption))
            }
        )
        ListItem(
            headlineContent = {
                Text(text = stringResource(id = CommonStrings.action_view_source))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.screen_advanced_settings_view_source_description))
            },
            trailingContent = ListItemContent.Switch(
                checked = state.isDeveloperModeEnabled,
            ),
            onClick = { state.eventSink(AdvancedSettingsEvents.SetDeveloperModeEnabled(!state.isDeveloperModeEnabled)) }
        )
        ListItem(
            headlineContent = {
                Text(text = stringResource(id = R.string.screen_advanced_settings_share_presence))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.screen_advanced_settings_share_presence_description))
            },
            trailingContent = ListItemContent.Switch(
                checked = state.isSharePresenceEnabled,
            ),
            onClick = { state.eventSink(AdvancedSettingsEvents.SetSharePresenceEnabled(!state.isSharePresenceEnabled)) }
        )
        val compressImages = state.mediaOptimizationState?.shouldCompressImages

        when (state.mediaOptimizationState) {
            null -> Unit
            is MediaOptimizationState.AllMedia -> {
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(id = R.string.screen_advanced_settings_media_compression_title))
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.screen_advanced_settings_media_compression_description))
                    },
                    trailingContent = ListItemContent.Switch(
                        checked = compressImages ?: false,
                    ),
                    onClick = {
                        val newValue = !(compressImages ?: false)
                        analyticsService.captureInteraction(
                            if (newValue) {
                                Interaction.Name.MobileSettingsOptimizeMediaUploadsEnabled
                            } else {
                                Interaction.Name.MobileSettingsOptimizeMediaUploadsDisabled
                            }
                        )
                        state.eventSink(AdvancedSettingsEvents.SetCompressMedia(newValue))
                    }
                )
            }
            is MediaOptimizationState.Split -> {
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(id = R.string.screen_advanced_settings_optimise_image_upload_quality_title))
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.screen_advanced_settings_optimise_image_upload_quality_description))
                    },
                    trailingContent = ListItemContent.Switch(
                        checked = compressImages ?: false,
                    ),
                    onClick = {
                        val newValue = !(compressImages ?: false)
                        analyticsService.captureInteraction(
                            if (newValue) {
                                Interaction.Name.MobileSettingsOptimizeMediaUploadsEnabled
                            } else {
                                Interaction.Name.MobileSettingsOptimizeMediaUploadsDisabled
                            }
                        )
                        state.eventSink(AdvancedSettingsEvents.SetCompressMedia(newValue))
                    }
                )

                var displaySelectorDialog by remember { mutableStateOf(false) }

                ListItem(
                    headlineContent = {
                        Text(text = stringResource(id = R.string.screen_advanced_settings_optimise_video_upload_quality_title))
                    },
                    supportingContent = {
                        val description = stringResource(id = R.string.screen_advanced_settings_optimise_video_upload_quality_description)
                        val quality = when (state.mediaOptimizationState.videoPreset) {
                            VideoCompressionPreset.LOW -> stringResource(id = R.string.screen_advanced_settings_optimise_video_upload_quality_low)
                            VideoCompressionPreset.STANDARD -> stringResource(id = R.string.screen_advanced_settings_optimise_video_upload_quality_standard)
                            VideoCompressionPreset.HIGH -> stringResource(id = R.string.screen_advanced_settings_optimise_video_upload_quality_high)
                        }
                        val descriptionWithValue = remember(quality) {
                            String.format(description, quality)
                        }
                        Text(text = descriptionWithValue)
                    },
                    onClick = { displaySelectorDialog = true },
                )

                if (displaySelectorDialog) {
                    VideoQualitySelectorDialog(
                        selectedPreset = state.mediaOptimizationState.videoPreset,
                        onSubmit = { preset ->
                            state.eventSink(AdvancedSettingsEvents.SetVideoUploadQuality(preset))
                            displaySelectorDialog = false
                        },
                        onDismiss = { displaySelectorDialog = false },
                    )
                }
            }
        }

        // Local state for the input field to allow smooth typing
        var maxCreditsInput by remember(state.maxCredits) {
            mutableStateOf(state.maxCredits?.toString() ?: "")
        }
        var isFocused by remember { mutableStateOf(false) }

        // Show error snackbar if wallet action fails
        LaunchedEffect(state.walletAction) {
            if (state.walletAction is AsyncAction.Failure) {
                snackbarDispatcher.post(SnackbarMessage(CommonStrings.common_error))
                state.eventSink(AdvancedSettingsEvents.ClearWalletActionError)
            }
        }

        PreferenceCategory(
            title = stringResource(id = CommonStrings.common_wallet),
            showTopDivider = true,
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
                                    // Focus lost - auto save if changed
                                    if (maxCreditsInput.isNotEmpty() &&
                                        maxCreditsInput != (state.originalMaxCredits?.toString() ?: "")) {
                                        state.eventSink(AdvancedSettingsEvents.SaveMaxCredits)
                                    }
                                }
                                isFocused = focusState.isFocused
                            }
                            .border(
                                width = 1.dp,
                                color = Color.Black,
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
                                if (maxCreditsInput.isNotEmpty()) {
                                    state.eventSink(AdvancedSettingsEvents.SaveMaxCredits)
                                }
                            }
                        ),
                        singleLine = true,
                    )
                }
            }
        }

        ModerationAndSafety(state)
    }
}

@Composable
private fun VideoQualitySelectorDialog(
    selectedPreset: VideoCompressionPreset,
    onSubmit: (VideoCompressionPreset) -> Unit,
    onDismiss: () -> Unit
) {
    val videoPresets = VideoCompressionPreset.entries
    var localSelectedPreset by remember { mutableStateOf(selectedPreset) }
    ListDialog(
        title = stringResource(CommonStrings.dialog_video_quality_selector_title),
        subtitle = stringResource(CommonStrings.dialog_default_video_quality_selector_subtitle),
        onSubmit = { onSubmit(localSelectedPreset) },
        onDismissRequest = onDismiss,
        applyPaddingToContents = false,
    ) {
        for (preset in videoPresets) {
            val isSelected = preset == localSelectedPreset
            item(
                key = preset,
                contentType = preset,
            ) {
                val title = when (preset) {
                    VideoCompressionPreset.LOW -> stringResource(R.string.screen_advanced_settings_optimise_video_upload_quality_low)
                    VideoCompressionPreset.STANDARD -> stringResource(R.string.screen_advanced_settings_optimise_video_upload_quality_standard)
                    VideoCompressionPreset.HIGH -> stringResource(R.string.screen_advanced_settings_optimise_video_upload_quality_high)
                }
                val subtitle = when (preset) {
                    VideoCompressionPreset.LOW -> stringResource(CommonStrings.common_video_quality_low_description)
                    VideoCompressionPreset.STANDARD -> stringResource(CommonStrings.common_video_quality_standard_description)
                    VideoCompressionPreset.HIGH -> stringResource(CommonStrings.common_video_quality_high_description)
                }
                ListItem(
                    headlineContent = {
                        Text(
                            text = title,
                            style = ElementTheme.typography.fontBodyLgMedium,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = subtitle,
                            style = ElementTheme.typography.fontBodyMdRegular,
                            color = ElementTheme.colors.textSecondary,
                        )
                    },
                    leadingContent = ListItemContent.RadioButton(
                        selected = isSelected,
                    ),
                    onClick = {
                        localSelectedPreset = preset
                    },
                )
            }
        }
    }
}

@Composable
private fun ModerationAndSafety(
    state: AdvancedSettingsState,
    modifier: Modifier = Modifier,
) {
    PreferenceCategory(
        modifier = modifier,
        title = stringResource(R.string.screen_advanced_settings_moderation_and_safety_section_title),
        showTopDivider = true
    ) {
        PreferenceSwitch(
            title = stringResource(R.string.screen_advanced_settings_hide_invite_avatars_toggle_title),
            isChecked = state.mediaPreviewConfigState.hideInviteAvatars,
            onCheckedChange = {
                state.eventSink(AdvancedSettingsEvents.SetHideInviteAvatars(it))
            },
            enabled = !state.mediaPreviewConfigState.setHideInviteAvatarsAction.isLoading()
        )
        ListSectionHeader(
            title = stringResource(R.string.screen_advanced_settings_show_media_timeline_title),
            hasDivider = false,
            description = {
                ListSupportingText(
                    text = stringResource(R.string.screen_advanced_settings_show_media_timeline_subtitle),
                    contentPadding = ListSupportingTextDefaults.Padding.None,
                )
            }
        )
        ListItem(
            headlineContent = { Text(text = stringResource(R.string.screen_advanced_settings_show_media_timeline_always_hide)) },
            leadingContent = ListItemContent.RadioButton(
                selected = state.mediaPreviewConfigState.timelineMediaPreviewValue == MediaPreviewValue.Off,
                compact = true
            ),
            onClick = {
                state.eventSink(AdvancedSettingsEvents.SetTimelineMediaPreviewValue(MediaPreviewValue.Off))
            },
            enabled = !state.mediaPreviewConfigState.setTimelineMediaPreviewAction.isLoading()
        )
        ListItem(
            headlineContent = { Text(text = stringResource(R.string.screen_advanced_settings_show_media_timeline_private_rooms)) },
            leadingContent = ListItemContent.RadioButton(
                selected = state.mediaPreviewConfigState.timelineMediaPreviewValue == MediaPreviewValue.Private,
                compact = true
            ),
            onClick = {
                state.eventSink(AdvancedSettingsEvents.SetTimelineMediaPreviewValue(MediaPreviewValue.Private))
            },
            enabled = !state.mediaPreviewConfigState.setTimelineMediaPreviewAction.isLoading()
        )
        ListItem(
            headlineContent = { Text(text = stringResource(R.string.screen_advanced_settings_show_media_timeline_always_show)) },
            leadingContent = ListItemContent.RadioButton(
                selected = state.mediaPreviewConfigState.timelineMediaPreviewValue == MediaPreviewValue.On,
                compact = true
            ),
            onClick = {
                state.eventSink(AdvancedSettingsEvents.SetTimelineMediaPreviewValue(MediaPreviewValue.On))
            },
            enabled = !state.mediaPreviewConfigState.setTimelineMediaPreviewAction.isLoading()
        )
    }
}

@PreviewWithLargeHeight
@Composable
internal fun AdvancedSettingsViewLightPreview(@PreviewParameter(AdvancedSettingsStateProvider::class) state: AdvancedSettingsState) =
    ElementPreviewLight { ContentToPreview(state) }

@PreviewWithLargeHeight
@Composable
internal fun AdvancedSettingsViewDarkPreview(@PreviewParameter(AdvancedSettingsStateProvider::class) state: AdvancedSettingsState) =
    ElementPreviewDark { ContentToPreview(state) }

@ExcludeFromCoverage
@Composable
private fun ContentToPreview(state: AdvancedSettingsState) {
    AdvancedSettingsView(
        state = state,
        onBackClick = { }
    )
}

@Composable
@PreviewsDayNight
internal fun VideoQualitySelectorDialogPreview() {
    ElementPreview {
        VideoQualitySelectorDialog(
            selectedPreset = VideoCompressionPreset.STANDARD,
            onSubmit = { /* no-op */ },
            onDismiss = { /* no-op */ }
        )
    }
}
