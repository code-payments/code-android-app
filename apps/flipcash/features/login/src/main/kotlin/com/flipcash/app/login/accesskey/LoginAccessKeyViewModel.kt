package com.flipcash.app.login.accesskey

import com.flipcash.app.accesskey.BaseAccessKeyViewModel
import com.flipcash.app.analytics.Action
import com.flipcash.app.analytics.Button
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.core.storage.MediaSaver
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.user.UserManager
import com.getcode.libs.qr.QRCodeGenerator
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class LoginAccessKeyViewModel @Inject constructor(
    resources: ResourceHelper,
    mnemonicManager: MnemonicManager,
    qrCodeGenerator: QRCodeGenerator,
    mediaSaver: MediaSaver,
    userManager: UserManager,
    private val userFlags: UserFlagsCoordinator,
    private val authManager: AuthManager,
    private val analytics: FlipcashAnalyticsService,
): BaseAccessKeyViewModel(resources, mnemonicManager, mediaSaver, userManager, qrCodeGenerator) {

    suspend fun onWroteDownInstead(): Result<Boolean> {
        trackButton(Button.WroteAccessKey)
        uiFlow.update { it.copy(skipState = LoadingSuccessState(loading = true)) }
        return runCatching {
            authManager.onUserAccessKeySeen()
            authManager.presentCredentialStorage()
            delay(150)
            uiFlow.update { s -> s.copy(skipState = LoadingSuccessState(success = true)) }
            userFlags.resolvedFlags.value.requiresIapForRegistration.effectiveValue
        }
    }

    suspend fun saveImage(): Result<Boolean> {
        trackButton(Button.SaveAccessKey)
        return saveBitmapToFile()
            .onSuccess { authManager.onUserAccessKeySeen() }
            .mapCatching {
                authManager.presentCredentialStorage()
                delay(150)
                uiFlow.update { s -> s.copy(exportState = LoadingSuccessState(success = true)) }
                userFlags.resolvedFlags.value.requiresIapForRegistration.effectiveValue
            }
    }

    private fun trackButton(button: Button): Result<Unit> {
        analytics.buttonTapped(button)
        return Result.success(Unit)
    }
}