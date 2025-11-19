/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.roomlist

import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.roomlist.RoomSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.RoomListEntriesUpdate
import org.matrix.rustcomponents.sdk.RoomListServiceInterface
import timber.log.Timber
import java.util.LinkedList
import kotlin.coroutines.CoroutineContext
import kotlin.time.measureTime

class RoomSummaryListProcessor(
    private val roomSummaries: MutableSharedFlow<List<RoomSummary>>,
    private val roomListService: RoomListServiceInterface,
    private val coroutineContext: CoroutineContext,
    private val roomSummaryDetailsFactory: RoomSummaryFactory = RoomSummaryFactory(),
    coroutineDispatchers: CoroutineDispatchers,
) {
    private val modifyPendingJobsMutex = Mutex()
    private val updateSummariesMutex = Mutex()

    private val coroutineScope = CoroutineScope(coroutineContext + coroutineDispatchers.computation)
    private val pendingUpdateJobs = LinkedList<Job>()

    suspend fun postUpdate(updates: List<RoomListEntriesUpdate>) {
        val first = updates.firstOrNull()

        modifyPendingJobsMutex.withLock {
            // We can cancel any pending updates if we receive a Reset or Clear
            if (first is RoomListEntriesUpdate.Reset || first is RoomListEntriesUpdate.Clear) {
                while (pendingUpdateJobs.isNotEmpty()) {
                    pendingUpdateJobs.removeFirst().cancel()
                }
            }

            val job = coroutineScope.launch {
                updateRoomSummaries {
                    Timber.v("Update rooms from postUpdates (with ${updates.size} items) on ${Thread.currentThread()}")
                    val elapsed = measureTime {
                        for (update in updates) {
                            applyUpdate(update)
                        }

                        // TODO remove once https://github.com/element-hq/element-x-android/issues/5031 has been confirmed as fixed
                        val duplicates = groupingBy { it.roomId }.eachCount().filter { it.value > 1 }
                        if (duplicates.isNotEmpty()) {
                            Timber.e(
                                "Found duplicates in room summaries after a list update from the SDK: $duplicates. Updates: ${updates.map { it.describe() }}"
                            )
                        }
                    }
                    Timber.d("Time to apply all updates: $elapsed")

                    modifyPendingJobsMutex.withLock {
                        // Remove the current job from the pending ones (done at the end so it can be cancelled)
                        if (pendingUpdateJobs.isNotEmpty()) {
                            pendingUpdateJobs.removeFirst()
                        }
                    }
                }
            }

            pendingUpdateJobs.add(job)
        }
    }

    suspend fun rebuildRoomSummaries() {
        updateRoomSummaries {
            forEachIndexed { i, summary ->
                val result = buildRoomSummaryForIdentifier(summary.roomId.value)
                if (result != null) {
                    this[i] = result
                }
            }
        }
    }

    private suspend fun MutableList<RoomSummary>.applyUpdate(update: RoomListEntriesUpdate) {
        // Remove this comment to debug changes in the room list
        // Timber.d("Apply room list update: ${update.describe(includeRoomNames = true)}")
        when (update) {
            is RoomListEntriesUpdate.Append -> {
                val roomSummaries = update.values.map {
                    buildSummaryForRoomListEntry(it)
                }
                addAll(roomSummaries)
            }
            is RoomListEntriesUpdate.PushBack -> {
                val roomSummary = buildSummaryForRoomListEntry(update.value)
                add(roomSummary)
            }
            is RoomListEntriesUpdate.PushFront -> {
                val roomSummary = buildSummaryForRoomListEntry(update.value)
                add(0, roomSummary)
            }
            is RoomListEntriesUpdate.Set -> {
                val roomSummary = buildSummaryForRoomListEntry(update.value)
                this[update.index.toInt()] = roomSummary
            }
            is RoomListEntriesUpdate.Insert -> {
                val roomSummary = buildSummaryForRoomListEntry(update.value)
                add(update.index.toInt(), roomSummary)
            }
            is RoomListEntriesUpdate.Remove -> {
                removeAt(update.index.toInt())
            }
            is RoomListEntriesUpdate.Reset -> {
                clear()
                addAll(update.values.map { buildSummaryForRoomListEntry(it) })
            }
            RoomListEntriesUpdate.PopBack -> {
                removeLastOrNull()
            }
            RoomListEntriesUpdate.PopFront -> {
                removeFirstOrNull()
            }
            RoomListEntriesUpdate.Clear -> {
                clear()
            }
            is RoomListEntriesUpdate.Truncate -> {
                subList(update.length.toInt(), size).clear()
            }
        }
    }

    private suspend fun buildSummaryForRoomListEntry(entry: Room): RoomSummary {
        return entry.use { roomSummaryDetailsFactory.create(room = it) }
    }

    private suspend fun buildRoomSummaryForIdentifier(identifier: String): RoomSummary? {
        return roomListService.roomOrNull(identifier)?.let { room ->
            buildSummaryForRoomListEntry(room)
        }
    }

    private suspend fun updateRoomSummaries(block: suspend MutableList<RoomSummary>.() -> Unit) = withContext(coroutineContext) {
        updateSummariesMutex.withLock {
            val current = roomSummaries.replayCache.lastOrNull()
            val mutableRoomSummaries = current?.toMutableList() ?: mutableListOf()
            block(mutableRoomSummaries)
            roomSummaries.emit(mutableRoomSummaries)
        }
    }
}
