/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.clarivaauth

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.network.wallet.GoogleSignInResult
import io.element.android.libraries.network.wallet.requestGoogleIdToken
import kotlinx.coroutines.launch

@ContributesNode(AppScope::class)
@AssistedInject
class ClarivaAuthNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: ClarivaAuthPresenter,
) : Node(buildContext, plugins = plugins) {
    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        val coroutineScope = rememberCoroutineScope()
        // Credential Manager needs an Activity to host its bottom sheet; fall back
        // to the composition-local context if the activity is somehow unavailable.
        val activity = LocalActivity.current
        val context = LocalContext.current

        ClarivaAuthView(
            state = state,
            modifier = modifier,
            onBackClick = ::navigateUp,
            onGoogleSignInClick = {
                coroutineScope.launch {
                    when (val result = requestGoogleIdToken(activity ?: context)) {
                        is GoogleSignInResult.Success ->
                            state.eventSink(ClarivaAuthEvents.SubmitGoogleIdToken(result.idToken))
                        // Dismissing the sheet is a deliberate choice, not a
                        // failure - leave the form exactly as it was.
                        GoogleSignInResult.Cancelled -> Unit
                        is GoogleSignInResult.Failure ->
                            state.eventSink(ClarivaAuthEvents.GoogleSignInFailed(result.message))
                    }
                }
            },
        )
    }
}
