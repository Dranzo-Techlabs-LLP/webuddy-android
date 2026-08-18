/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.scanqrcode.impl.scanuser

sealed interface ScanUserQrCodeEvents {
    data class QrCodeScanned(val code: ByteArray) : ScanUserQrCodeEvents
    data object TryAgain : ScanUserQrCodeEvents
    data object RequestCameraPermission : ScanUserQrCodeEvents
}
