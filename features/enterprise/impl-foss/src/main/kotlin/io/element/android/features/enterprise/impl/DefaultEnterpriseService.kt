/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.enterprise.impl

import androidx.compose.ui.graphics.Color
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.appconfig.OnBoardingConfig
import io.element.android.compound.colors.SemanticColorsLightDark
import io.element.android.features.enterprise.api.BugReportUrl
import io.element.android.features.enterprise.api.EnterpriseService
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@ContributesBinding(AppScope::class)
class DefaultEnterpriseService : EnterpriseService {
    override val isEnterpriseBuild = false

    override suspend fun isEnterpriseUser(sessionId: SessionId) = false

    /**
     * Clariva offers exactly one account provider. Returning a non-empty list
     * here is what suppresses the "change account provider" affordances in
     * onboarding.
     */
    override fun defaultHomeserverList(): List<String> = listOf(OnBoardingConfig.ACCOUNT_PROVIDER)

    /**
     * Closed deployment: only Clariva's own hosts may be connected to.
     *
     * Compares the PARSED host for exact equality. A `startsWith`/`contains`
     * check would accept `clarivahub.com.attacker.net`, and ignoring userinfo
     * would accept `https://clarivahub.com@attacker.net/`.
     */
    override suspend fun isAllowedToConnectToHomeserver(homeserverUrl: String): Boolean {
        val host = parseHost(homeserverUrl) ?: return false
        return host in OnBoardingConfig.ALLOWED_HOMESERVER_HOSTS
    }

    private fun parseHost(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        // Strip scheme, then path/query/fragment, then userinfo, then port.
        val withoutScheme = trimmed.substringAfter("://", trimmed)
        val authority = withoutScheme
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        val host = authority
            .substringAfterLast('@')
            .substringBefore(':')
            .lowercase()
        return host.ifEmpty { null }
    }

    override suspend fun overrideBrandColor(sessionId: SessionId?, brandColor: String?) = Unit

    override fun brandColorsFlow(sessionId: SessionId?): Flow<Color?> {
        return flowOf(null)
    }

    override fun semanticColorsFlow(sessionId: SessionId?): Flow<SemanticColorsLightDark> {
        return flowOf(SemanticColorsLightDark.default)
    }

    override fun firebasePushGateway(): String? = null
    override fun unifiedPushDefaultPushGateway(): String? = null

    override fun bugReportUrlFlow(sessionId: SessionId?): Flow<BugReportUrl> {
        return flowOf(BugReportUrl.UseDefault)
    }
}
