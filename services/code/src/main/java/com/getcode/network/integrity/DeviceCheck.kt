package com.getcode.network.integrity

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Base64
import com.codeinc.gen.common.v1.Model
import com.fingerprintjs.android.fpjs_pro.Configuration
import com.fingerprintjs.android.fpjs_pro.FingerprintJS
import com.fingerprintjs.android.fpjs_pro.FingerprintJSFactory
import com.getcode.api.BuildConfig
import com.getcode.api.BuildConfig.GOOGLE_CLOUD_PROJECT_NUMBER
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManager
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.play.core.integrity.IntegrityTokenResponse
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class DeviceTokenResult(
    val token: String?
)

object DeviceCheck: CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private lateinit var integrityManager: IntegrityManager

    private var fingerprint: FingerprintJS? = null
    private lateinit var deviceId: String

    @SuppressLint("HardwareIds")
    fun register(context: Context) {
        integrityManager = IntegrityManagerFactory.create(context)
        deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

        fingerprint = runCatching {
            val factory = FingerprintJSFactory(context)
            val configuration = Configuration(
                apiKey = BuildConfig.FINGERPRINT_API_KEY,
                region = Configuration.Region.US,
            )

            factory.createInstance(configuration)
        }.getOrNull()
    }

    private fun handleAppCheckError(error: Throwable): Boolean {
        error.printStackTrace()
        return true
    }

    @Deprecated("Replace with Result variant")
    fun integrityResponseSingle(): Single<DeviceTokenResult> {
        return Single.create { emitter ->
            tokenResponse(
                onToken = { emitter.onSuccess(DeviceTokenResult(it)) },
                onError = { emitter.onError(it) }
            )
        }
    }

    @Deprecated("Replace with Result variant")
    fun integrityResponseFlowable(
        backpressureStrategy: BackpressureStrategy = BackpressureStrategy.BUFFER
    ): Flowable<DeviceTokenResult> {
        return Flowable.create({ emitter ->
            tokenResponse(
                onToken = { emitter.onNext(DeviceTokenResult(it)) },
                onError = { emitter.onError(it) }
            )
        }, backpressureStrategy)
    }

    suspend fun integrityResponse(): DeviceTokenResult = suspendCancellableCoroutine { cont ->
        tokenResponse(
            onToken = { cont.resume(DeviceTokenResult(it)) },
            onError = { cont.resumeWithException(it) }
        )
    }

    private fun visitorIdentifier(onIdentifier: (Result<String?>) -> Unit) {
        fingerprint?.getVisitorId(
            listener = {
                onIdentifier(Result.success(it.visitorId)) },
            errorListener = {
                val error = Throwable("Device Check failed:: ${it.description}")
//                ErrorUtils.handleError(error)
//                onIdentifier(Result.failure(error))
                onIdentifier(Result.success(null))
            }
        ) ?: onIdentifier(Result.success(null))
    }
    private fun tokenResponse(onToken: (String?) -> Unit, onError: (Throwable) -> Unit) {
        visitorIdentifier { result ->
            if (result.isFailure) {
                onError(result.exceptionOrNull()!!)
                return@visitorIdentifier
            }

            val tag = result.getOrNull()
            val rawNonce = "$tag $deviceId"

            val nonce = Base64.encodeToString(rawNonce.toByteArray(), Base64.NO_WRAP)
            // Request the integrity token by providing a nonce.
            val integrityTokenResponse: Task<IntegrityTokenResponse> =
                integrityManager.requestIntegrityToken(
                    IntegrityTokenRequest.builder()
                        .setCloudProjectNumber(GOOGLE_CLOUD_PROJECT_NUMBER.toLong())
                        .setNonce(nonce)
                        .build()
                )

            integrityTokenResponse.addOnSuccessListener {
                onToken(it.token())
            }.addOnFailureListener { error ->
                if (!handleAppCheckError(error)) {
                    onError(error)
                    return@addOnFailureListener
                }

                onToken(null)
            }
        }
    }
}

fun String.toDeviceToken() = Model.DeviceToken.newBuilder().setValue(this).build()