/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.startchat.api

/**
 * Failure of [StartDMAction] when a consultant tries to start a new DM with another consultant:
 * consultants may only chat with clients. Retrying cannot succeed, so views show it as a plain
 * warning (no retry button) using [TITLE] and [message].
 */
class ConsultantChatNotAllowedException : Exception(MESSAGE) {
    companion object {
        const val TITLE = "Can't start chat"
        const val MESSAGE = "You can't chat with other consultants. Consultants can only chat with clients."
    }
}
