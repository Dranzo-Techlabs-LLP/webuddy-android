/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.myqrcode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.permalink.PermalinkBuilder

@Inject
class MyQrCodePresenter(
    private val matrixClient: MatrixClient,
    private val permalinkBuilder: PermalinkBuilder,
) : Presenter<MyQrCodeState> {

    @Composable
    override fun present(): MyQrCodeState {
        val matrixUser by matrixClient.userProfile.collectAsState()
        val permalink = remember {
            val me = UserId(matrixClient.sessionId.value)
            permalinkBuilder.permalinkForUser(me).getOrNull()
        }
        return MyQrCodeState(
            matrixUser = matrixUser,
            permalink = permalink,
        )
    }
}
