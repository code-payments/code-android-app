package com.getcode.view.main.account

import android.annotation.SuppressLint
import com.getcode.navigation.core.CodeNavigator
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.login.BaseAccessKeyViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject


@HiltViewModel
class AccountAccessKeyViewModel @Inject constructor(
    resources: ResourceHelper,
) : BaseAccessKeyViewModel(resources) {
    @SuppressLint("CheckResult")
    fun onSubmit(navigator: CodeNavigator) {
        Completable.create {
            val result = saveBitmapToFile()
            if (result) it.onComplete() else it.onError(IllegalStateException())
        }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.computation())
            .doOnSubscribe {
                uiFlow.value = uiFlow.value.copy(isLoading = true, isEnabled = false)
            }
            .doOnComplete {
                uiFlow.value =
                    uiFlow.value.copy(isLoading = false, isEnabled = false, isSuccess = true)
            }
            .doOnError {
                uiFlow.value =
                    uiFlow.value.copy(isLoading = false, isEnabled = true, isSuccess = false)
            }
            .subscribe({}, ::onSubmitError)
    }
}