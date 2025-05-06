package com.flipcash.app.login.accesskey

import com.flipcash.app.accesskey.BaseAccessKeyViewModel
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.core.storage.MediaScanner
import com.flipcash.services.user.UserManager
import com.getcode.libs.qr.QRCodeGenerator
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.util.resources.ResourceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LoginAccessKeyViewModel @Inject constructor(
    resources: ResourceHelper,
    mnemonicManager: MnemonicManager,
    mediaScanner: MediaScanner,
    userManager: UserManager,
    qrCodeGenerator: QRCodeGenerator,
    private val authManager: AuthManager
): BaseAccessKeyViewModel(resources, mnemonicManager, mediaScanner, userManager, qrCodeGenerator) {

    suspend fun saveImage(): Result<Unit> = saveBitmapToFile()
        .onSuccess { authManager.onUserAccessKeySeen() }
        .map { Unit }

    suspend fun onWroteDownInstead(): Result<Unit> = authManager.onUserAccessKeySeen()
}