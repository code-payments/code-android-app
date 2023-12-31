package com.getcode.view.login

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.navigation.NavController
import com.getcode.App
import com.getcode.R
import com.getcode.crypt.MnemonicPhrase
import com.getcode.manager.AnalyticsManager
import com.getcode.manager.AuthManager
import com.getcode.manager.TopBarManager
import com.getcode.network.repository.ApiDeniedException
import com.getcode.network.repository.getPublicKeyBase58
import com.getcode.view.*
import javax.inject.Inject
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit


@HiltViewModel
class AccessKeyViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val analyticsManager: AnalyticsManager
) : BaseAccessKeyViewModel() {
    fun onSubmit(navController: NavController?, isSaveImage: Boolean, isDeepLink: Boolean = false) {
        val entropyB64 = uiFlow.value.entropyB64 ?: return

        authManager.login(App.getInstance(), entropyB64, rollbackOnError = isDeepLink)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.computation())
            .doOnSubscribe {
                uiFlow.value = uiFlow.value.copy(isLoading = true, isEnabled = false)
            }
            .concatWith(
                if (isSaveImage) {
                    Completable.create { c ->
                        val result = saveBitmapToFile()
                        if (result) c.onComplete() else c.onError(IllegalStateException())
                    }.subscribeOn(Schedulers.computation())
                } else {
                    Completable.complete()
                }
            )
            .doOnComplete {
                uiFlow.value = uiFlow.value.copy(isLoading = false, isSuccess = true)
            }
            .delay(2L, TimeUnit.SECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    onComplete(navController, entropyB64)
                },
                {
                    onSubmitError(it)
                    analyticsManager.createAccount(false, null)
                    uiFlow.value = uiFlow.value.copy(isLoading = false)
                    navController?.navigate(LoginSections.LOGIN.route)
                }
            )
    }

    private fun onComplete(navController: NavController?, entropyB64: String) {
        val owner = MnemonicPhrase.fromEntropyB64(App.getInstance(), entropyB64)
            .getSolanaKeyPair(App.getInstance())
        analyticsManager.createAccount(true, owner.getPublicKeyBase58())

        val cameraPermissionDenied = ContextCompat.checkSelfPermission(
            App.getInstance(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_DENIED

        if (cameraPermissionDenied) {
            navController?.navigate(LoginSections.PERMISSION_CAMERA_REQUEST.route)
        } else {
            if (Build.VERSION.SDK_INT < 33) {
                navController?.navigate(MainSections.HOME.route)
            } else {
                val notificationsPermissionDenied = ContextCompat.checkSelfPermission(
                    App.getInstance(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_DENIED

                if (notificationsPermissionDenied) {
                    navController?.navigate(LoginSections.PERMISSION_NOTIFICATION_REQUEST.route)
                } else {
                    navController?.navigate(MainSections.HOME.route)
                }
            }
        }
    }

}