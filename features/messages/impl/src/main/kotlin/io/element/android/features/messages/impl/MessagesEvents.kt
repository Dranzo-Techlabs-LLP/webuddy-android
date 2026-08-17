/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl

import io.element.android.features.messages.impl.actionlist.model.TimelineItemAction
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.libraries.matrix.api.timeline.item.event.EventOrTransactionId
import io.element.android.libraries.matrix.api.user.MatrixUser

sealed interface MessagesEvents {
    data class HandleAction(val action: TimelineItemAction, val event: TimelineItem.Event) : MessagesEvents
    data class ToggleReaction(val emoji: String, val eventOrTransactionId: EventOrTransactionId) : MessagesEvents
    data class InviteDialogDismissed(val action: InviteDialogAction) : MessagesEvents
    data class OnUserClicked(val user: MatrixUser) : MessagesEvents
    data object Dismiss : MessagesEvents
    data object MarkAsFullyReadAndExit : MessagesEvents
    data class Summarize(val duration: SummaryDuration) : MessagesEvents
    data class AskAI(val question: String) : MessagesEvents
    data object DismissSummary : MessagesEvents
    data object RequestRefund : MessagesEvents

    /**
     * Consultant approves the client's refund. [refundAmount] null => full refund (whole held
     * amount back to the client). A positive value is a PARTIAL refund: only that much returns
     * to the client and the consultant keeps the remainder (credited immediately server-side).
     */
    data class ApproveRefund(val refundAmount: Double? = null) : MessagesEvents
    data object RejectRefund : MessagesEvents
}

enum class SummaryDuration {
    LastDay,
    LastWeek,
    LastMonth
}

enum class InviteDialogAction {
    Cancel,
    Invite,
}
