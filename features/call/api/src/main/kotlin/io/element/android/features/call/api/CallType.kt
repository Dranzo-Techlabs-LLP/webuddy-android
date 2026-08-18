/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.api

import android.os.Parcelable
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.parcelize.Parcelize

sealed interface CallType : NodeInputs, Parcelable {
    @Parcelize
    data class ExternalUrl(val url: String) : CallType {
        override fun toString(): String {
            return "ExternalUrl"
        }
    }

    @Parcelize
    data class RoomCall(
        val sessionId: SessionId,
        val roomId: RoomId,
        /**
         * When true the call is started as a "voice call": for a DM room this selects Element
         * Call's *_DM_VOICE widget intent, which starts the call with the camera off (audio on).
         * Element Call has no native audio-only mode; the voice intent is how it's expressed. For a
         * non-DM room there is no voice intent, so the camera-off request falls back to the pre-join
         * lobby where the user can turn the camera off manually.
         */
        val startWithVideoMuted: Boolean = false,
    ) : CallType {
        override fun toString(): String {
            return "RoomCall(sessionId=$sessionId, roomId=$roomId, startWithVideoMuted=$startWithVideoMuted)"
        }
    }
}
