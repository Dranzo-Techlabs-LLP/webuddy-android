/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.scanqrcode.impl.scanuser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.atomic.pages.FlowStepPage
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.modifiers.cornerBorder
import io.element.android.libraries.designsystem.modifiers.squareSize
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.permissions.api.PermissionsView
import io.element.android.libraries.qrcode.QrCodeCameraView

@Composable
fun ScanUserQrCodeView(
    state: ScanUserQrCodeState,
    onBackClick: () -> Unit,
    onUserScanned: (UserId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val updatedOnUserScanned by rememberUpdatedState(onUserScanned)
    LaunchedEffect(state.scannedUserId) {
        state.scannedUserId?.let { updatedOnUserScanned(it) }
    }

    // Displays the "open settings" dialog if the camera permission has been permanently denied.
    PermissionsView(
        state = state.cameraPermissionState,
    )

    FlowStepPage(
        modifier = modifier,
        onBackClick = onBackClick,
        iconStyle = BigIcon.Style.Default(CompoundIcons.QrCode()),
        title = "Scan QR code",
        content = { Content(state = state) },
        buttons = { Buttons(state = state) },
    )
}

@Composable
private fun Content(
    state: ScanUserQrCodeState,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val modifier = if (constraints.maxWidth > constraints.maxHeight) {
            Modifier.fillMaxHeight()
        } else {
            Modifier.fillMaxWidth()
        }.then(
            Modifier
                .padding(start = 20.dp, end = 20.dp, top = 50.dp, bottom = 32.dp)
                .squareSize()
                .cornerBorder(
                    strokeWidth = 4.dp,
                    color = ElementTheme.colors.textPrimary,
                    cornerSizeDp = 42.dp,
                )
        )
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            if (state.cameraPermissionState.permissionGranted) {
                QrCodeCameraView(
                    modifier = Modifier.fillMaxSize(),
                    onScanQrCode = { state.eventSink(ScanUserQrCodeEvents.QrCodeScanned(it)) },
                    isScanning = state.isScanning,
                )
            } else {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = "Camera access is required to scan a QR code.",
                    textAlign = TextAlign.Center,
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.Buttons(
    state: ScanUserQrCodeState,
) {
    Column(Modifier.heightIn(min = 130.dp)) {
        when {
            state.isInvalidQrCode -> {
                Button(
                    text = "Try again",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    onClick = { state.eventSink(ScanUserQrCodeEvents.TryAgain) },
                )
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Not a valid user QR code.",
                    textAlign = TextAlign.Center,
                    color = ElementTheme.colors.textCriticalPrimary,
                    style = ElementTheme.typography.fontBodySmMedium,
                )
            }
            !state.cameraPermissionState.permissionGranted -> {
                Button(
                    text = "Grant camera access",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { state.eventSink(ScanUserQrCodeEvents.RequestCameraPermission) },
                )
            }
        }
    }
}
