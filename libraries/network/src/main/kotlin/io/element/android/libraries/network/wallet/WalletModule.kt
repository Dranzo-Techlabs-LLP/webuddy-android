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
import io.element.android.appconfig.ClarivaConfig
import io.element.android.libraries.network.RetrofitFactory

@BindingContainer
@ContributesTo(AppScope::class)
object WalletModule {
    @Provides
    @SingleIn(AppScope::class)
    fun providesWalletApi(retrofitFactory: RetrofitFactory): WalletApi {
        // Base URL lives in ClarivaConfig so the auth API and the wallet API can
        // never drift onto different hosts. 10.0.2.2 is the emulator's view of
        // the host machine and is already allow-listed for cleartext in
        // res/xml/network_security_config.xml.
        return retrofitFactory.create(ClarivaConfig.API_BASE_URL)
            .create(WalletApi::class.java)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providesClarivaAuthApi(retrofitFactory: RetrofitFactory): ClarivaAuthApi {
        return retrofitFactory.create(ClarivaConfig.API_BASE_URL)
            .create(ClarivaAuthApi::class.java)
    }
}
