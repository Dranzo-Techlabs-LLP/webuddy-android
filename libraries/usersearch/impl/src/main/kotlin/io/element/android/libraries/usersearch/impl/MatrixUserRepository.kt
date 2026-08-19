/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.usersearch.impl

import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.MatrixPatterns
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.usersearch.api.UserListDataSource
import io.element.android.libraries.usersearch.api.UserRepository
import io.element.android.libraries.usersearch.api.UserSearchResult
import io.element.android.libraries.usersearch.api.UserSearchResultState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@ContributesBinding(SessionScope::class)
class MatrixUserRepository(
    private val client: MatrixClient,
    private val dataSource: UserListDataSource
) : UserRepository {
    override fun search(query: String): Flow<UserSearchResultState> = flow {
        val trimmed = query.trim()
        val isFullUserId = MatrixPatterns.isUserId(trimmed) && !client.isMe(UserId(trimmed))
        // A bare username ("mohamed.shimil4") resolves against OUR homeserver's domain — taken
        // from the session id, never hardcoded — so users can be found without typing the full
        // Matrix id. Only well-formed localparts qualify; anything else stays a plain text search.
        val derivedUserId = if (!isFullUserId && LOCALPART_REGEX.matches(trimmed)) {
            val domain = client.sessionId.value.substringAfter(':', missingDelimiterValue = "")
            "@${trimmed.lowercase()}:$domain"
                .takeIf { domain.isNotEmpty() && MatrixPatterns.isUserId(it) && !client.isMe(UserId(it)) }
        } else {
            null
        }
        val shouldFetchSearchResults = trimmed.length >= MINIMUM_SEARCH_LENGTH
        // If the search term is a MXID that's not ours, we'll show a 'fake' result for that user, then update it when we get search results.
        // (Derived usernames don't get a fake row up-front: they only appear once their profile
        // actually resolves, so a display-name search never shows a bogus unknown-user entry.)
        val fakeSearchResult = if (isFullUserId) {
            UserSearchResult(MatrixUser(UserId(trimmed)))
        } else {
            null
        }
        if (isFullUserId || shouldFetchSearchResults) {
            emit(UserSearchResultState(isSearching = shouldFetchSearchResults, results = listOfNotNull(fakeSearchResult)))
        }
        if (shouldFetchSearchResults) {
            val results = fetchSearchResults(
                query = trimmed,
                explicitUserId = trimmed.takeIf { isFullUserId },
                derivedUserId = derivedUserId,
            )
            emit(results)
        }
    }

    private suspend fun fetchSearchResults(
        query: String,
        explicitUserId: String?,
        derivedUserId: String?,
    ): UserSearchResultState {
        // Debounce
        delay(DEBOUNCE_TIME_MILLIS)
        val results = dataSource
            .search(query, MAXIMUM_SEARCH_RESULTS)
            .filter { !client.isMe(it.userId) }
            .map { UserSearchResult(it) }
            .toMutableList()

        // If the query is another user's MXID and the result doesn't contain that user ID, query the profile information explicitly
        if (explicitUserId != null && results.none { it.matrixUser.userId.value == explicitUserId }) {
            results.add(
                0,
                dataSource.getProfile(UserId(explicitUserId))
                    ?.let { UserSearchResult(it) }
                    ?: UserSearchResult(MatrixUser(UserId(explicitUserId)), isUnresolved = true)
            )
        }

        // Bare-username lookup: surface "@<query>:<our domain>" at the top — but ONLY when that
        // profile genuinely exists, so free-text searches don't grow a phantom result.
        if (derivedUserId != null && results.none { it.matrixUser.userId.value == derivedUserId }) {
            dataSource.getProfile(UserId(derivedUserId))?.let { profile ->
                results.add(0, UserSearchResult(profile))
            }
        }

        return UserSearchResultState(results = results, isSearching = false)
    }

    companion object {
        private const val DEBOUNCE_TIME_MILLIS = 250L
        private const val MINIMUM_SEARCH_LENGTH = 3
        private const val MAXIMUM_SEARCH_RESULTS = 10L

        /**
         * Matrix localpart charset (a-z, 0-9, `.` `_` `=` `-` `/` `+`), case-insensitive input.
         * No `@` or `:` — those mean the user is typing a full Matrix id.
         */
        private val LOCALPART_REGEX = Regex("^[a-zA-Z0-9._=/+-]+$")
    }
}
