/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.filters.selection

import dev.zacsweers.metro.ContributesBinding
import io.element.android.appconfig.ClarivaConfig
import io.element.android.features.home.impl.filters.RoomListFilter
import io.element.android.libraries.di.SessionScope
import kotlinx.coroutines.flow.MutableStateFlow

@ContributesBinding(SessionScope::class)
class DefaultFilterSelectionStrategy : FilterSelectionStrategy {
    // The "Rooms" chip only exists while group rooms are enabled. The enum entry stays, because
    // exhaustive whens elsewhere still refer to it.
    private val availableFilters: List<RoomListFilter> = if (ClarivaConfig.GROUP_ROOMS_ENABLED) {
        RoomListFilter.entries
    } else {
        RoomListFilter.entries - RoomListFilter.Rooms
    }
    private val selectedFilters = LinkedHashSet<RoomListFilter>()

    override val filterSelectionStates = MutableStateFlow(buildFilters())

    override fun select(filter: RoomListFilter) {
        if (filter !in availableFilters) return
        selectedFilters.add(filter)
        filterSelectionStates.value = buildFilters()
    }

    override fun deselect(filter: RoomListFilter) {
        selectedFilters.remove(filter)
        filterSelectionStates.value = buildFilters()
    }

    override fun isSelected(filter: RoomListFilter): Boolean {
        return selectedFilters.contains(filter)
    }

    override fun clear() {
        selectedFilters.clear()
        filterSelectionStates.value = buildFilters()
    }

    private fun buildFilters(): Set<FilterSelectionState> {
        val selectedFilterStates = selectedFilters.map {
            FilterSelectionState(
                filter = it,
                isSelected = true
            )
        }
        val unselectedFilters = availableFilters - selectedFilters - selectedFilters.flatMap { it.incompatibleFilters }.toSet()
        val unselectedFilterStates = unselectedFilters.map {
            FilterSelectionState(
                filter = it,
                isSelected = false
            )
        }
        return (selectedFilterStates + unselectedFilterStates).toSet()
    }
}
