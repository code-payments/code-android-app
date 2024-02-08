package com.getcode.network.appcheck

import com.codeinc.gen.common.v1.Model
import com.getcode.api.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.appcheck.AppCheckToken
import com.google.firebase.appcheck.AppCheckTokenResult
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.debug.internal.DebugAppCheckProvider
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import timber.log.Timber
import java.lang.Exception

data class DeviceTokenResult(
    val token: AppCheckToken?
)

object AppCheck {

    fun register() {
//        if (BuildConfig.DEBUG) {
            Firebase.appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
//        } else {
//            Firebase.appCheck.installAppCheckProviderFactory(
//                PlayIntegrityAppCheckProviderFactory.getInstance()
//            )
//        }
    }

    private fun handleAppCheckError(error: Exception): Boolean {
        val match = "code: \\d+".toRegex().find(error.message.orEmpty())
        val errorCode = match?.value?.removePrefix("code: ")?.toInt() ?: -1
        Timber.e("Failed to get appcheck token: errorCode=$errorCode ${error.message}")

        return errorCode == 403 // bad attestation
                || errorCode >= 500
    }

    @Deprecated("Replace with Flow variant")
    fun limitedUseTokenSingle(): Single<DeviceTokenResult> {
        return Single.create { emitter ->
            Firebase.appCheck.limitedUseAppCheckToken
                .addOnSuccessListener {
                    Timber.d("attestation passed")
                    emitter.onSuccess(DeviceTokenResult(it))
                }
                .addOnFailureListener { error ->
                    if (!handleAppCheckError(error)) {
                        emitter.onError(error)
                        return@addOnFailureListener
                    }

                    emitter.onSuccess(DeviceTokenResult(null))
                }
        }
    }

    @Deprecated("Replace with Flow variant")
    fun limitedUseTokenFlowable(
        backpressureStrategy: BackpressureStrategy = BackpressureStrategy.BUFFER
    ): Flowable<DeviceTokenResult> {
        return Flowable.create({ emitter ->
            Firebase.appCheck.limitedUseAppCheckToken
                .addOnSuccessListener {
                    Timber.d("attestation passed")
                    emitter.onNext(DeviceTokenResult(it))
                }
                .addOnFailureListener { error ->
                    if (!handleAppCheckError(error)) {
                        emitter.onError(error)
                        return@addOnFailureListener
                    }

                    emitter.onNext(DeviceTokenResult(null))
                }
        }, backpressureStrategy)
    }

    fun limitedUseToken(
        backpressureStrategy: BackpressureStrategy = BackpressureStrategy.BUFFER
    ): Flow<DeviceTokenResult> {
        return limitedUseTokenFlowable(backpressureStrategy).asFlow()
    }
}

fun AppCheckToken.toDeviceToken() = Model.DeviceToken.newBuilder().setValue(this.token).build()
fun String.toDeviceToken() = Model.DeviceToken.newBuilder().setValue(this).build()