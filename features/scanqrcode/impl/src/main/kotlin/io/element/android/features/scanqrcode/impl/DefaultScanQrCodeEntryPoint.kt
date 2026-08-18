/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.scanqrcode.impl

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.scanqrcode.api.ScanQrCodeEntryPoint
import io.element.android.features.scanqrcode.impl.scanuser.ScanUserQrCodeNode
import io.element.android.libraries.architecture.createNode

@ContributesBinding(AppScope::class)
class DefaultScanQrCodeEntryPoint : ScanQrCodeEntryPoint {
    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        callback: ScanQrCodeEntryPoint.Callback,
    ): Node {
        return parentNode.createNode<ScanUserQrCodeNode>(buildContext, listOf(callback))
    }
}
