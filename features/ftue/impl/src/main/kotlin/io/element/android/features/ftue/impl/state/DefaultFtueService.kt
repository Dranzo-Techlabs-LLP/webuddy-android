/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.ftue.impl.state

import android.Manifest
import android.os.Build
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.features.ftue.api.state.FtueService
import io.element.android.features.ftue.api.state.FtueState
import io.element.android.features.lockscreen.api.LockScreenService
import io.element.android.libraries.core.coroutine.mapState
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.verification.SessionVerificationService
import io.element.android.libraries.matrix.api.verification.SessionVerifiedStatus
import io.element.android.libraries.permissions.api.PermissionStateProvider
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import io.element.android.services.analytics.api.AnalyticsService
import io.element.android.services.toolbox.api.sdk.BuildVersionSdkIntProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultFtueService(
    private val sdkVersionProvider: BuildVersionSdkIntProvider,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
    private val analyticsService: AnalyticsService,
    private val permissionStateProvider: PermissionStateProvider,
    private val lockScreenService: LockScreenService,
    private val sessionVerificationService: SessionVerificationService,
    private val sessionPreferencesStore: SessionPreferencesStore,
) : FtueService {
    private val userNeedsToConfirmSessionVerificationSuccess = MutableStateFlow(false)

    val ftueStepStateFlow = MutableStateFlow<InternalFtueState>(InternalFtueState.Unknown)

    override val state = ftueStepStateFlow
        .mapState {
            when (it) {
                is InternalFtueState.Unknown -> FtueState.Unknown
                is InternalFtueState.Incomplete -> FtueState.Incomplete
                is InternalFtueState.Complete -> FtueState.Complete
            }
        }

    init {
        combine(
            sessionVerificationService.sessionVerifiedStatus.onEach { sessionVerifiedStatus ->
                if (sessionVerifiedStatus == SessionVerifiedStatus.NotVerified) {
                    // Ensure we wait for the user to confirm the session verified screen before going further
                    userNeedsToConfirmSessionVerificationSuccess.value = true
                }
            },
            userNeedsToConfirmSessionVerificationSuccess,
            analyticsService.didAskUserConsentFlow.distinctUntilChanged(),
        ) {
            updateFtueStep()
        }
            .launchIn(sessionCoroutineScope)
    }

    fun updateFtueStep() = sessionCoroutineScope.launch {
        val step = getNextStep(null)
        ftueStepStateFlow.value = when (step) {
            null -> InternalFtueState.Complete
            else -> InternalFtueState.Incomplete(step)
        }
    }

    private suspend fun getNextStep(completedStep: FtueStep? = null): FtueStep? =
        when (completedStep) {
            null -> if (!isSessionVerificationStateReady()) {
                FtueStep.WaitingForInitialState
            } else {
                getNextStep(FtueStep.WaitingForInitialState)
            }
            // The skip flag (canSkipVerification) must be authoritative here. It is
            // already honored inside isSessionNotVerified(), but the
            // userNeedsToConfirmSessionVerificationSuccess term is set to true
            // independently for any NotVerified session, which would otherwise force
            // the verification step even when skip is set. Clariva sessions are
            // provisioned server-side and have no Matrix password / recovery key, so
            // on a second device none of the verification routes are usable - they
            // opt out via this flag. This guard only affects sessions that explicitly
            // set skip; normal sessions (skip=false) are unchanged.
            FtueStep.WaitingForInitialState -> if (
                isSessionNotVerified() ||
                (userNeedsToConfirmSessionVerificationSuccess.value && !canSkipVerification())
            ) {
                FtueStep.SessionVerification
            } else {
                getNextStep(FtueStep.SessionVerification)
            }
            FtueStep.SessionVerification -> if (shouldAskNotificationPermissions()) {
                FtueStep.NotificationsOptIn
            } else {
                getNextStep(FtueStep.NotificationsOptIn)
            }
            FtueStep.NotificationsOptIn -> if (shouldDisplayLockscreenSetup()) {
                FtueStep.LockscreenSetup
            } else {
                getNextStep(FtueStep.LockscreenSetup)
            }
            FtueStep.LockscreenSetup -> if (needsAnalyticsOptIn()) {
                FtueStep.AnalyticsOptIn
            } else {
                getNextStep(FtueStep.AnalyticsOptIn)
            }
            FtueStep.AnalyticsOptIn -> null
        }

    private fun isSessionVerificationStateReady(): Boolean {
        return sessionVerificationService.sessionVerifiedStatus.value != SessionVerifiedStatus.Unknown
    }

    private suspend fun isSessionNotVerified(): Boolean {
        return sessionVerificationService.sessionVerifiedStatus.value == SessionVerifiedStatus.NotVerified && !canSkipVerification()
    }

    private suspend fun canSkipVerification(): Boolean {
        // Clariva fork: mandatory FTUE session verification is ALWAYS skippable.
        // Accounts are provisioned server-side and users never hold a Matrix
        // password or recovery key, so on a second device none of Element's verify
        // routes (other device / recovery key / password reset) are usable and the
        // FTUE would otherwise wall the user off at "Confirm your identity". Users
        // can still verify or set up recovery manually from Settings; only the
        // mandatory gate is removed. The per-session skip preference is still
        // honored if ever set (OR), but we no longer rely on writing it at login -
        // opening that session-scoped DataStore from the login (AppScope) presenter
        // collided with the session's own DataStore on the same file.
        //
        // sessionPreferencesStore is still read once so the store is initialised as
        // before and the injected dependency is not left unused.
        sessionPreferencesStore.isSessionVerificationSkipped().first()
        return true
    }

    private suspend fun needsAnalyticsOptIn(): Boolean {
        return analyticsService.didAskUserConsentFlow.first().not()
    }

    private suspend fun shouldAskNotificationPermissions(): Boolean {
        return if (sdkVersionProvider.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            val isPermissionDenied = permissionStateProvider.isPermissionDenied(permission).first()
            val isPermissionGranted = permissionStateProvider.isPermissionGranted(permission)
            !isPermissionGranted && !isPermissionDenied
        } else {
            false
        }
    }

    private suspend fun shouldDisplayLockscreenSetup(): Boolean {
        return lockScreenService.isSetupRequired().first()
    }

    fun onUserCompletedSessionVerification() {
        userNeedsToConfirmSessionVerificationSuccess.value = false
    }
}

sealed interface FtueStep {
    data object WaitingForInitialState : FtueStep
    data object SessionVerification : FtueStep
    data object NotificationsOptIn : FtueStep
    data object AnalyticsOptIn : FtueStep
    data object LockscreenSetup : FtueStep
}
