/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.scanqrcode.impl.scanuser

import android.Manifest
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.permalink.PermalinkData
import io.element.android.libraries.matrix.api.permalink.PermalinkParser
import io.element.android.libraries.permissions.api.PermissionsEvent
import io.element.android.libraries.permissions.api.PermissionsPresenter

@Inject
class ScanUserQrCodePresenter(
    private val permalinkParser: PermalinkParser,
    permissionsPresenterFactory: PermissionsPresenter.Factory,
) : Presenter<ScanUserQrCodeState> {
    private val cameraPermissionPresenter: PermissionsPresenter = permissionsPresenterFactory.create(Manifest.permission.CAMERA)

    private var isScanning by mutableStateOf(true)
    private var isInvalidQrCode by mutableStateOf(false)
    private var scannedUserId by mutableStateOf<UserId?>(null)
    private var permissionRequested by mutableStateOf(false)

    @Composable
    override fun present(): ScanUserQrCodeState {
        val cameraPermissionState = cameraPermissionPresenter.present()

        // Request the camera permission automatically the first time the screen is shown.
        LaunchedEffect(cameraPermissionState.permissionGranted) {
            if (!cameraPermissionState.permissionGranted && !permissionRequested) {
                permissionRequested = true
                cameraPermissionState.eventSink(PermissionsEvent.RequestPermissions)
            }
        }

        fun handleEvent(event: ScanUserQrCodeEvents) {
            when (event) {
                is ScanUserQrCodeEvents.QrCodeScanned -> {
                    isScanning = false
                    val text = String(event.code, Charsets.UTF_8)
                    // Clariva QR codes encode a clarivahub.com/u/<user id> link. Parse that first;
                    // fall back to the Matrix permalink parser so a matrix.to user QR still works.
                    val clarivaUserId = parseClarivaUserLink(text)
                    if (clarivaUserId != null) {
                        scannedUserId = clarivaUserId
                    } else when (val data = permalinkParser.parse(text)) {
                        is PermalinkData.UserLink -> scannedUserId = data.userId
                        else -> isInvalidQrCode = true
                    }
                }
                ScanUserQrCodeEvents.TryAgain -> {
                    isInvalidQrCode = false
                    isScanning = true
                }
                ScanUserQrCodeEvents.RequestCameraPermission -> {
                    cameraPermissionState.eventSink(PermissionsEvent.RequestPermissions)
                }
            }
        }

        return ScanUserQrCodeState(
            isScanning = isScanning &&
                cameraPermissionState.permissionGranted &&
                !isInvalidQrCode &&
                scannedUserId == null,
            isInvalidQrCode = isInvalidQrCode,
            scannedUserId = scannedUserId,
            cameraPermissionState = cameraPermissionState,
            eventSink = ::handleEvent,
        )
    }
}

/**
 * Parse a Clariva user deep link (https://clarivahub.com/u/<matrix user id>) to its [UserId], or
 * null if [text] isn't one. Uri.pathSegments returns already-decoded segments, so an encoded id
 * like %40user%3Aclarivahub.com round-trips back to @user:clarivahub.com.
 */
private fun parseClarivaUserLink(text: String): UserId? {
    val uri = runCatching { Uri.parse(text.trim()) }.getOrNull() ?: return null
    if (!uri.host.equals("clarivahub.com", ignoreCase = true)) return null
    val segments = uri.pathSegments
    if (segments.size < 2 || segments[0] != "u") return null
    return segments[1].takeIf { it.startsWith("@") && it.contains(":") }?.let { UserId(it) }
}
