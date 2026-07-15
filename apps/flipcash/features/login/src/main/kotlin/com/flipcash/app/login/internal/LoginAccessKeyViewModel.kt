package com.flipcash.app.login.internal

import com.flipcash.app.accesskey.BaseAccessKeyViewModel
import com.flipcash.app.analytics.Button
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.core.storage.MediaSaver
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.features.login.R
import com.flipcash.services.user.UserManager
import com.getcode.libs.qr.QRCodeGenerator
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
internal class LoginAccessKeyViewModel @Inject constructor(
    private val resources: ResourceHelper,
    mnemonicManager: MnemonicManager,
    qrCodeGenerator: QRCodeGenerator,
    mediaSaver: MediaSaver,
    userManager: UserManager,
    dispatchers: DispatcherProvider,
    private val userFlags: UserFlagsCoordinator,
    private val featureFlags: FeatureFlagController,
    private val authManager: AuthManager,
    private val analytics: FlipcashAnalyticsService,
): BaseAccessKeyViewModel(resources, mnemonicManager, mediaSaver, userManager, qrCodeGenerator, dispatchers) {

    suspend fun onWroteDownInstead(): Result<Boolean> {
        trackButton(Button.WroteAccessKey)
        uiFlow.update { it.copy(skipState = LoadingSuccessState(loading = true)) }
        return runCatching {
            authManager.onUserAccessKeySeen()
            authManager.presentCredentialStorage()
            val requiresIap = userFlags.resolvedFlags.value.requiresIapForRegistration.effectiveValue
            if (!requiresIap) {
                // Gate: provision the core account before advancing. On failure this
                // throws, runCatching yields Result.failure, and the screen stays put.
                authManager.ensureCoreAccountProvisioned()
                    .onFailure { showAccountSetupError() }
                    .getOrThrow()
            }
            delay(150)
            uiFlow.update { s -> s.copy(skipState = LoadingSuccessState(success = true)) }
            requiresIap
        }.onFailure {
            // Reset the spinner so the button is tappable again to retry.
            uiFlow.update { s -> s.copy(skipState = LoadingSuccessState()) }
        }
    }

    suspend fun saveImage(): Result<Boolean> {
        trackButton(Button.SaveAccessKey)
        return saveBitmapToFile()
            .onSuccess { authManager.onUserAccessKeySeen() }
            .mapCatching {
                authManager.presentCredentialStorage()
                val requiresIap = userFlags.resolvedFlags.value.requiresIapForRegistration.effectiveValue
                if (!requiresIap) {
                    // Gate: provision the core account before advancing. On failure this
                    // throws, mapCatching yields Result.failure, and the screen stays put.
                    authManager.ensureCoreAccountProvisioned()
                        .onFailure { showAccountSetupError() }
                        .getOrThrow()
                }
                delay(150)
                uiFlow.update { s -> s.copy(exportState = LoadingSuccessState(success = true)) }
                requiresIap
            }
    }

    private fun showAccountSetupError() {
        BottomBarManager.showError(
            title = resources.getString(R.string.error_title_accountSetupFailed),
            message = resources.getString(R.string.error_description_accountSetupFailed),
        )
    }

    private fun trackButton(button: Button): Result<Unit> {
        analytics.buttonTapped(button)
        return Result.success(Unit)
    }
}