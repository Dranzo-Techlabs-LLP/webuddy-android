/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.lastseen

import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.network.presence.PresencePrivacy
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

data class LastSeenPrivacyState(
    val privacy: PresencePrivacy,
    /** People with a DM both have joined, offered by "My contacts except..."; null while loading. */
    val contacts: ImmutableList<MatrixUser>?,
    /** Which of [contacts] are currently excluded from seeing the last seen. */
    val excludedUserIds: ImmutableSet<UserId>,
    val isPickingExceptions: Boolean,
    val saveAction: AsyncAction<Unit>,
    val eventSink: (LastSeenPrivacyEvents) -> Unit,
)
