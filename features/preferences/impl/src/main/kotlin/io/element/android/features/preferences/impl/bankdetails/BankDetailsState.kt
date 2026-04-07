/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.bankdetails

import io.element.android.libraries.architecture.AsyncAction

data class BankDetailsState(
    val bankName: String,
    val accountNumber: String,
    val ifscCode: String,
    val accountHolderName: String,
    val branchName: String,
    val saveAction: AsyncAction<Unit>,
    val eventSink: (BankDetailsEvents) -> Unit
)
