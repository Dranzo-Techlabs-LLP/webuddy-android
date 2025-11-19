/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.roomlist

import io.element.android.libraries.architecture.coverage.ExcludeFromCoverage
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.RoomListEntriesUpdate

@Suppress("unused")
@ExcludeFromCoverage
internal fun RoomListEntriesUpdate.describe(includeRoomNames: Boolean = false): String {
    return when (this) {
        is RoomListEntriesUpdate.Set -> {
            "Set #$index to ${roomDescription(value, includeRoomNames)}"
        }
        is RoomListEntriesUpdate.Append -> {
            "Append ${values.map { roomDescription(it, includeRoomNames) }}"
        }
        is RoomListEntriesUpdate.PushBack -> {
            "PushBack ${roomDescription(value, includeRoomNames)}"
        }
        is RoomListEntriesUpdate.PushFront -> {
            "PushFront ${roomDescription(value, includeRoomNames)}"
        }
        is RoomListEntriesUpdate.Insert -> {
            "Insert at #$index: ${roomDescription(value, includeRoomNames)}"
        }
        is RoomListEntriesUpdate.Remove -> {
            "Remove #$index"
        }
        is RoomListEntriesUpdate.Reset -> {
            "Reset all to ${values.map { roomDescription(it, includeRoomNames) }}"
        }
        RoomListEntriesUpdate.PopBack -> {
            "PopBack"
        }
        RoomListEntriesUpdate.PopFront -> {
            "PopFront"
        }
        RoomListEntriesUpdate.Clear -> {
            "Clear"
        }
        is RoomListEntriesUpdate.Truncate -> {
            "Truncate to $length items"
        }
    }
}

private fun roomDescription(room: Room, includeRoomNames: Boolean): String {
    return if (includeRoomNames) {
        "'${room.displayName()}' - ${room.id()}"
    } else {
        room.id()
    }
}
