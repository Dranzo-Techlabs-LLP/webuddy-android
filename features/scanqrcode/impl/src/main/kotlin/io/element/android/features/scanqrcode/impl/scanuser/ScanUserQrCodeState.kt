/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.scanqrcode.impl.scanuser

import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.permissions.api.PermissionsState

data class ScanUserQrCodeState(
    val isScanning: Boolean,
    val isInvalidQrCode: Boolean,
    val scannedUserId: UserId?,
    val cameraPermissionState: PermissionsState,
    val eventSink: (ScanUserQrCodeEvents) -> Unit,
)
