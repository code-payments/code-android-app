package com.flipcash.app.backupkey.internal

import com.flipcash.app.accesskey.BaseAccessKeyViewModel
import com.flipcash.app.core.storage.MediaScanner
import com.flipcash.services.user.UserManager
import com.getcode.libs.qr.QRCodeGenerator
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.util.resources.ResourceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class BackupKeyScreenViewModel @Inject constructor(
    resources: ResourceHelper,
    mnemonicManager: MnemonicManager,
    mediaScanner: MediaScanner,
    userManager: UserManager,
    qrCodeGenerator: QRCodeGenerator,
) : BaseAccessKeyViewModel(
    resources,
    mnemonicManager,
    mediaScanner,
    userManager,
    qrCodeGenerator
) {
    suspend fun saveImage(): Result<Unit> = saveBitmapToFile()
        .map { Unit }

}