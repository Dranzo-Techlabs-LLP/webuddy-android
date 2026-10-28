/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.lastseen

import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.network.presence.LastSeenAudience
import io.element.android.libraries.network.presence.OnlineAudience

sealed interface LastSeenPrivacyEvents {
    /** Picking "My contacts except..." opens the contact picker instead of saving straight away. */
    data class SetLastSeenAudience(val audience: LastSeenAudience) : LastSeenPrivacyEvents
    data class SetOnlineAudience(val audience: OnlineAudience) : LastSeenPrivacyEvents
    data class SaveExceptions(val excludedUserIds: Set<UserId>) : LastSeenPrivacyEvents
    data object CancelExceptions : LastSeenPrivacyEvents
    data object ClearSaveAction : LastSeenPrivacyEvents
}
