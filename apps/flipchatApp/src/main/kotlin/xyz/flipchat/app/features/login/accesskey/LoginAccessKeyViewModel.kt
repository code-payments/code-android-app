package xyz.flipchat.app.features.login.accesskey

import com.getcode.libs.qr.QRCodeGenerator
import com.getcode.services.manager.MnemonicManager
import com.getcode.util.resources.ResourceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import xyz.flipchat.app.features.accesskey.BaseAccessKeyViewModel
import xyz.flipchat.app.util.media.MediaScanner
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject

@HiltViewModel
class LoginAccessKeyViewModel @Inject constructor(
    resources: ResourceHelper,
    mnemonicManager: MnemonicManager,
    mediaScanner: MediaScanner,
    userManager: UserManager,
    qrCodeGenerator: QRCodeGenerator
): BaseAccessKeyViewModel(resources, mnemonicManager, mediaScanner, userManager, qrCodeGenerator) {

    suspend fun saveImage(): Result<Unit> = saveBitmapToFile().map { Unit }
}