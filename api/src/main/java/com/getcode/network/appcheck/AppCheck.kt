package com.getcode.network.appcheck

import com.codeinc.gen.common.v1.Model
import com.google.firebase.Firebase
import com.google.firebase.appcheck.AppCheckToken
import com.google.firebase.appcheck.appCheck
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow

object AppCheck {

    @Deprecated("Replace with Flow variant")
    fun limitedUseTokenSingle(): Single<AppCheckToken> {
        return Single.create { emitter ->
            Firebase.appCheck.limitedUseAppCheckToken
                .addOnSuccessListener { emitter.onSuccess(it) }
                .addOnFailureListener { emitter.onError(it) }
        }
    }

    @Deprecated("Replace with Flow variant")
    fun limitedUseTokenFlowable(
        backpressureStrategy: BackpressureStrategy = BackpressureStrategy.BUFFER
    ): Flowable<AppCheckToken> {
        return Flowable.create({ emitter ->
            Firebase.appCheck.limitedUseAppCheckToken
                .addOnSuccessListener { emitter.onNext(it) }
                .addOnFailureListener { emitter.onError(it) }
        }, backpressureStrategy)
    }

    fun limitedUseToken(
        backpressureStrategy: BackpressureStrategy = BackpressureStrategy.BUFFER
    ): Flow<AppCheckToken> {
        return limitedUseTokenFlowable(backpressureStrategy).asFlow()
    }
}

fun AppCheckToken.toDeviceToken() = Model.DeviceToken.newBuilder().setValue(token).build()
fun String.toDeviceToken() = Model.DeviceToken.newBuilder().setValue(this).build()