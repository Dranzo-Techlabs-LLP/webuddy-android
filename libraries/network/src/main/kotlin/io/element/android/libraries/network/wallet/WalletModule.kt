/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.network.wallet

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.network.RetrofitFactory

@BindingContainer
@ContributesTo(AppScope::class)
object WalletModule {
    @Provides
    @SingleIn(AppScope::class)
    fun providesWalletApi(retrofitFactory: RetrofitFactory): WalletApi {
        return retrofitFactory.create("http://10.0.2.2:3000/")
            .create(WalletApi::class.java)
    }
}
