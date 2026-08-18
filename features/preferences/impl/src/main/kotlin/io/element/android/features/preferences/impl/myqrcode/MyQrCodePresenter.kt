/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.myqrcode

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.MatrixClient

@Inject
class MyQrCodePresenter(
    private val matrixClient: MatrixClient,
) : Presenter<MyQrCodeState> {

    @Composable
    override fun present(): MyQrCodeState {
        val matrixUser by matrixClient.userProfile.collectAsState()
        val qrData = remember {
            // Clariva-branded deep link — NOT a matrix.to permalink, which routes external scanners
            // (phone camera / Google Lens) to matrix.to -> Element X on the Play Store. The Clariva
            // link is resolved in-app by the QR scanner and IntentResolver (-> the scanned user's
            // profile). Once clarivahub.com serves /.well-known/assetlinks.json and a /u/ fallback
            // page, an external scan opens Clariva if installed, else the Clariva Play Store page.
            CLARIVA_USER_LINK_PREFIX + Uri.encode(matrixClient.sessionId.value)
        }
        return MyQrCodeState(
            matrixUser = matrixUser,
            permalink = qrData,
        )
    }

    companion object {
        const val CLARIVA_USER_LINK_PREFIX = "https://clarivahub.com/u/"
    }
}
