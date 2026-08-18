/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.myqrcode

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.element.android.libraries.androidutils.system.startSharePlainTextIntent
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.ui.components.MatrixUserHeader
import io.element.android.libraries.qrcode.QrCodeImage
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.libraries.androidutils.R as AndroidUtilsR

@Composable
fun MyQrCodeView(
    state: MyQrCodeState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = "My QR code",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MatrixUserHeader(matrixUser = state.matrixUser)
            Spacer(modifier = Modifier.height(24.dp))
            val permalink = state.permalink
            if (permalink != null) {
                QrCodeImage(
                    data = permalink,
                    modifier = Modifier.size(220.dp),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    text = "Share link",
                    onClick = { onShareLink(context, permalink) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(text = "QR code is not available.")
            }
        }
    }
}

private fun onShareLink(
    context: Context,
    permalink: String,
) {
    context.startSharePlainTextIntent(
        activityResultLauncher = null,
        chooserTitle = context.getString(CommonStrings.action_share),
        text = permalink,
        noActivityFoundMessage = context.getString(AndroidUtilsR.string.error_no_compatible_app_found),
    )
}
