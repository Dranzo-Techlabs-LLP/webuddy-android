/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.billing

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.preferences.impl.advanced.AdvancedSettingsPresenter
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.di.SessionScope

/**
 * Consultant-only "Billing and Payments" screen. Reuses [AdvancedSettingsPresenter] (which already
 * owns the wallet / max-credits state and save logic) and renders only the wallet section, moved
 * out of the Advanced settings screen.
 */
@ContributesNode(SessionScope::class)
@AssistedInject
class BillingAndPaymentsNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: AdvancedSettingsPresenter,
) : Node(buildContext, plugins = plugins) {

    interface Callback : Plugin {
        fun navigateToBankDetails()
    }

    private val callback: Callback = callback()

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        BillingAndPaymentsView(
            state = state,
            modifier = modifier,
            onBackClick = ::navigateUp,
            onBankDetailsClick = callback::navigateToBankDetails,
        )
    }
}
