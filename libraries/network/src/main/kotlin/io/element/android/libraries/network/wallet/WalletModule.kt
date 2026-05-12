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
        // Use 10.0.2.2 for Android emulators to connect to the host's localhost. 
        // If you are using a physical device, you may need to use your machine's local IP address (e.g. 192.168.x.x) or use 'adb reverse tcp:3000 tcp:3000' and 'localhost'
        return retrofitFactory.create("https://wallet.dranzo.com/")
            .create(WalletApi::class.java)
    }
}
