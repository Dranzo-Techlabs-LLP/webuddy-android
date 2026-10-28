/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.lastseen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListSectionHeader
import io.element.android.libraries.designsystem.theme.components.ListSupportingText
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.components.CheckableUserRow
import io.element.android.libraries.matrix.ui.components.CheckableUserRowData
import io.element.android.libraries.matrix.ui.model.getAvatarData
import io.element.android.libraries.network.presence.LastSeenAudience
import io.element.android.libraries.network.presence.OnlineAudience
import io.element.android.libraries.network.presence.PresencePrivacy
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** WhatsApp-style "Last seen and online" privacy screen. */
@Composable
fun LastSeenPrivacyView(
    state: LastSeenPrivacyState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isPickingExceptions) {
        ExceptionsPicker(state = state, modifier = modifier)
    } else {
        PrivacyOptions(state = state, onBackClick = onBackClick, modifier = modifier)
    }
    AsyncActionView(
        async = state.saveAction,
        onSuccess = { state.eventSink(LastSeenPrivacyEvents.ClearSaveAction) },
        errorMessage = { "Couldn't save your choice. Please try again." },
        onErrorDismiss = { state.eventSink(LastSeenPrivacyEvents.ClearSaveAction) },
    )
}

@Composable
private fun PrivacyOptions(
    state: LastSeenPrivacyState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val privacy = state.privacy
    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = "Last seen and online",
    ) {
        ListSectionHeader(title = "Who can see my last seen", hasDivider = false)
        LastSeenAudience.entries.forEach { audience ->
            val isSelected = privacy.lastSeen == audience
            ListItem(
                headlineContent = { Text(audience.label()) },
                supportingContent = if (audience == LastSeenAudience.MY_CONTACTS_EXCEPT && isSelected) {
                    { Text(excludedSummary(privacy.lastSeenExcept.size)) }
                } else {
                    null
                },
                leadingContent = ListItemContent.RadioButton(selected = isSelected, compact = true),
                onClick = { state.eventSink(LastSeenPrivacyEvents.SetLastSeenAudience(audience)) },
            )
        }
        ListSectionHeader(title = "Who can see when I'm online")
        OnlineAudience.entries.forEach { audience ->
            ListItem(
                headlineContent = { Text(audience.label()) },
                leadingContent = ListItemContent.RadioButton(selected = privacy.online == audience, compact = true),
                onClick = { state.eventSink(LastSeenPrivacyEvents.SetOnlineAudience(audience)) },
            )
        }
        ListSupportingText(
            text = "If you don't share when you were last seen or online, you won't be able to see when other people were last seen or online.",
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
    }
}

/** "My contacts except...": pick who must not see the last seen. Back cancels without saving. */
@Composable
private fun ExceptionsPicker(
    state: LastSeenPrivacyState,
    modifier: Modifier = Modifier,
) {
    val cancel = { state.eventSink(LastSeenPrivacyEvents.CancelExceptions) }
    BackHandler(onBack = cancel)
    // A fresh selection each time the picker opens, starting from what is saved.
    val selection = remember { mutableStateOf<Set<UserId>>(state.excludedUserIds) }
    PreferencePage(
        modifier = modifier,
        onBackClick = cancel,
        title = "Hide my last seen from",
    ) {
        val contacts = state.contacts
        when {
            contacts == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            contacts.isEmpty() -> ListSupportingText(
                text = "You don't have any chats yet. People you chat with will appear here.",
                modifier = Modifier.padding(vertical = 16.dp),
            )
            else -> {
                ListSupportingText(
                    text = "Selected contacts won't see your last seen. Everyone else you chat with will.",
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                contacts.forEach { contact ->
                    CheckableUserRow(
                        checked = contact.userId in selection.value,
                        onCheckedChange = { checked ->
                            selection.value = if (checked) selection.value + contact.userId else selection.value - contact.userId
                        },
                        data = contact.toRowData(),
                    )
                }
            }
        }
        Button(
            text = "Done",
            onClick = { state.eventSink(LastSeenPrivacyEvents.SaveExceptions(selection.value)) },
            enabled = contacts != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
        )
    }
}

private fun MatrixUser.toRowData() = CheckableUserRowData.Resolved(
    avatarData = getAvatarData(AvatarSize.UserListItem),
    name = displayName ?: userId.value,
    subtext = userId.value.takeIf { displayName != null },
)

private fun LastSeenAudience.label() = when (this) {
    LastSeenAudience.EVERYONE -> "Everyone"
    LastSeenAudience.MY_CONTACTS -> "My contacts"
    LastSeenAudience.MY_CONTACTS_EXCEPT -> "My contacts except..."
    LastSeenAudience.NOBODY -> "Nobody"
}

private fun OnlineAudience.label() = when (this) {
    OnlineAudience.EVERYONE -> "Everyone"
    OnlineAudience.SAME_AS_LAST_SEEN -> "Same as last seen"
}

private fun excludedSummary(count: Int) = when (count) {
    0 -> "No contacts excluded"
    1 -> "1 contact excluded"
    else -> "$count contacts excluded"
}

internal class LastSeenPrivacyStateProvider : PreviewParameterProvider<LastSeenPrivacyState> {
    override val values = sequenceOf(
        aLastSeenPrivacyState(),
        aLastSeenPrivacyState(privacy = PresencePrivacy(LastSeenAudience.NOBODY, OnlineAudience.SAME_AS_LAST_SEEN)),
        aLastSeenPrivacyState(isPickingExceptions = true),
    )
}

private fun aLastSeenPrivacyState(
    privacy: PresencePrivacy = PresencePrivacy(),
    isPickingExceptions: Boolean = false,
) = LastSeenPrivacyState(
    privacy = privacy,
    contacts = persistentListOf(MatrixUser(UserId("@alice:clarivahub.com"), "Alice")),
    excludedUserIds = persistentSetOf(),
    isPickingExceptions = isPickingExceptions,
    saveAction = AsyncAction.Uninitialized,
    eventSink = {},
)

@PreviewsDayNight
@Composable
internal fun LastSeenPrivacyViewPreview(@PreviewParameter(LastSeenPrivacyStateProvider::class) state: LastSeenPrivacyState) = ElementPreview {
    LastSeenPrivacyView(state = state, onBackClick = {})
}
