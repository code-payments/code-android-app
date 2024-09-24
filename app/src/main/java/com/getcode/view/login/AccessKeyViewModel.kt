package com.getcode.view.login

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import com.getcode.AppHomeScreen
import com.getcode.analytics.Action
import com.getcode.analytics.ActionSource
import com.getcode.analytics.AnalyticsService
import com.getcode.manager.AuthManager
import com.getcode.manager.MnemonicManager
import com.getcode.media.MediaScanner
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.screens.CodeLoginPermission
import com.getcode.navigation.screens.ScanScreen
import com.getcode.navigation.screens.LoginScreen
import com.getcode.navigation.screens.PermissionRequestScreen
import com.getcode.network.repository.getPublicKeyBase58
import com.getcode.util.permissions.PermissionChecker
import com.getcode.util.resources.ResourceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import javax.inject.Inject


@HiltViewModel
class AccessKeyViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val analytics: AnalyticsService,
    private val permissions: PermissionChecker,
    private val mnemonicManager: MnemonicManager,
    resources: ResourceHelper,
    mediaScanner: MediaScanner,
) : BaseAccessKeyViewModel(resources, mnemonicManager, mediaScanner) {
    @SuppressLint("CheckResult")
    fun onSubmit(navigator: CodeNavigator, isSaveImage: Boolean, isDeepLink: Boolean = false) {
        val entropyB64 = uiFlow.value.entropyB64 ?: return

        authManager.createAccount(entropyB64, rollbackOnError = isDeepLink)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.computation())
            .doOnSubscribe {
                uiFlow.value = uiFlow.value.copy(isLoading = true, isEnabled = false)
            }
            .concatWith(
                if (isSaveImage) {
                    Completable.create { c ->
                        analytics.action(Action.ConfirmAccessKey, source = ActionSource.AccessKeySaved)
                        val result = saveBitmapToFile()
                        if (result) c.onComplete() else c.onError(IllegalStateException())
                    }.subscribeOn(Schedulers.computation())
                } else {
                    analytics.action(Action.ConfirmAccessKey, source = ActionSource.AccessKeyWroteDown)
                    Completable.complete()
                }
            )
            .doOnComplete {
                val owner = mnemonicManager.getKeyPair(entropyB64)
                analytics.createAccount(true, owner.getPublicKeyBase58())
                uiFlow.value = uiFlow.value.copy(isLoading = false, isSuccess = true)
            }
            .delay(2L, TimeUnit.SECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    onComplete(navigator, entropyB64)
                },
                {
                    onSubmitError(it)
                    analytics.createAccount(false, null)
                    uiFlow.value = uiFlow.value.copy(isLoading = false)
                    navigator.replaceAll(LoginScreen())
                }
            )
    }

    private fun onComplete(navigator: CodeNavigator, entropyB64: String) {
        val cameraPermissionDenied = permissions.isDenied(Manifest.permission.CAMERA)

        if (cameraPermissionDenied) {
            navigator.push(PermissionRequestScreen(CodeLoginPermission.Camera, true))
        } else {
            if (Build.VERSION.SDK_INT < 33) {
                analytics.action(Action.CompletedOnboarding)
                navigator.replaceAll(AppHomeScreen())
            } else {
                val notificationsPermissionDenied = permissions.isDenied(
                    Manifest.permission.POST_NOTIFICATIONS
                )

                if (notificationsPermissionDenied) {
                    navigator.push(PermissionRequestScreen(CodeLoginPermission.Notifications, true))
                } else {
                    analytics.action(Action.CompletedOnboarding)
                    navigator.replaceAll(AppHomeScreen())
                }
            }
        }
    }

}