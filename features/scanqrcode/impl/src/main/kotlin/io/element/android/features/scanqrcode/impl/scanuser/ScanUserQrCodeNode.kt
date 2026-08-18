/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.scanqrcode.impl.scanuser

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.scanqrcode.api.ScanQrCodeEntryPoint
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.di.SessionScope

@ContributesNode(SessionScope::class)
@AssistedInject
class ScanUserQrCodeNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: ScanUserQrCodePresenter,
) : Node(buildContext, plugins = plugins) {

    private val callback: ScanQrCodeEntryPoint.Callback = callback()

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        ScanUserQrCodeView(
            state = state,
            modifier = modifier,
            onBackClick = callback::onCancel,
            onUserScanned = callback::onUserScanned,
        )
    }
}
