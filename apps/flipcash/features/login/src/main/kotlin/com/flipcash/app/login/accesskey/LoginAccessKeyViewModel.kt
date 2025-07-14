package com.flipcash.app.login.accesskey

import com.flipcash.app.accesskey.BaseAccessKeyViewModel
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.core.storage.MediaScanner
import com.flipcash.services.user.UserManager
import com.getcode.libs.qr.QRCodeGenerator
import com.getcode.manager.TopBarManager
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.util.resources.ResourceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class LoginAccessKeyViewModel @Inject constructor(
    resources: ResourceHelper,
    mnemonicManager: MnemonicManager,
    qrCodeGenerator: QRCodeGenerator,
    mediaScanner: MediaScanner,
    private val userManager: UserManager,
    private val authManager: AuthManager
): BaseAccessKeyViewModel(resources, mnemonicManager, mediaScanner, userManager, qrCodeGenerator) {

    suspend fun saveImage(): Result<Boolean> = saveBitmapToFile()
        .onSuccess { authManager.onUserAccessKeySeen() }
        .map { authManager.presentCredentialStorage() }
        .map { userManager.userFlags?.requiresIapForRegistration == true }

    suspend fun onWroteDownInstead(): Result<Boolean> = authManager.onUserAccessKeySeen()
        .map { authManager.presentCredentialStorage() }
        .map { userManager.userFlags?.requiresIapForRegistration == true }
}