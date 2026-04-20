/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.wallet.impl

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import dev.zacsweers.metro.Inject
import io.element.android.features.wallet.api.WalletEntryPoint
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.di.SessionScope
import dev.zacsweers.metro.ContributesBinding

@ContributesBinding(SessionScope::class)
class WalletEntryPointImpl @Inject constructor() : WalletEntryPoint {
    override fun createNode(parentNode: Node, buildContext: BuildContext): Node {
        return parentNode.createNode<WalletNode>(buildContext)
    }
}
