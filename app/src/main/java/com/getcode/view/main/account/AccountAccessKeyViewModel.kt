package com.getcode.view.main.account

import android.annotation.SuppressLint
import androidx.lifecycle.viewModelScope
import com.getcode.libs.qr.QRCodeGenerator
import com.getcode.media.MediaScanner
import com.getcode.services.manager.MnemonicManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.login.BaseAccessKeyViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds


@HiltViewModel
class AccountAccessKeyViewModel @Inject constructor(
    resources: ResourceHelper,
    mnemonicManager: MnemonicManager,
    mediaScanner: MediaScanner,
    qrCodeGenerator: QRCodeGenerator,
) : BaseAccessKeyViewModel(resources, mnemonicManager, mediaScanner, qrCodeGenerator) {
    @SuppressLint("CheckResult")
    fun onSubmit() {
        Completable.create {
            val result = saveBitmapToFile()
            if (result) it.onComplete() else it.onError(IllegalStateException())
        }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.computation())
            .doOnSubscribe {
                uiFlow.update {
                    it.copy(isLoading = true, isEnabled = false)
                }
            }
            .doOnComplete {
                viewModelScope.launch {
                    uiFlow.update {
                        it.copy(isLoading = false, isEnabled = false, isSuccess = true)
                    }
                    // wait 2s and reset button state
                    delay(2.seconds)

                    uiFlow.update {
                        it.copy(isSuccess = false, isEnabled = true)
                    }

                }
            }
            .doOnError {
                uiFlow.update {
                    it.copy(isLoading = false, isEnabled = true, isSuccess = false)
                }
            }
            .subscribe({}, ::onSubmitError)
    }
}