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
import co.artise.android.recovery.api.SignInRecovery
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
    // Artise: the saved recovery key unlocks the chats without asking (artise/recovery).
    private val signInRecovery: SignInRecovery,
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
            // Artise: "Hacerlo después" and the chat list's unlock banner change this; the steps follow.
            sessionPreferencesStore.isSessionVerificationSkipped().distinctUntilChanged(),
        ) { _, _, _, _ ->
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
            FtueStep.WaitingForInitialState -> if (isSessionNotVerified() && signInRecovery.restore()) {
                // Artise: unlocked with the saved key, with nothing to confirm; the verified status follows, then this runs again.
                userNeedsToConfirmSessionVerificationSuccess.value = false
                FtueStep.WaitingForInitialState
            } else if (isSessionNotVerified() || userNeedsToConfirmSessionVerificationSuccess.value && !canSkipVerification()) {
                // Artise: after "Hacerlo después" (skipped) there's no success screen to wait for.
                FtueStep.SessionVerification
            } else {
                getNextStep(FtueStep.SessionVerification)
            }
            FtueStep.SessionVerification -> {
                setUpRecoveryOnce()
                if (shouldAskNotificationPermissions()) {
                    FtueStep.NotificationsOptIn
                } else {
                    getNextStep(FtueStep.NotificationsOptIn)
                }
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
        return sessionPreferencesStore.isSessionVerificationSkipped().first()
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

    private var recoverySetUpStarted = false

    // Artise: on an account's first phone, recovery is turned on with a key kept in the phone's encrypted backup.
    private fun setUpRecoveryOnce() {
        if (recoverySetUpStarted) return
        recoverySetUpStarted = true
        sessionCoroutineScope.launch { signInRecovery.setUpIfMissing() }
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
