package com.getcode.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import timber.log.Timber
import java.util.concurrent.TimeUnit

class OtpSmsBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent) {
        if (SmsRetriever.SMS_RETRIEVED_ACTION == intent.action) {
            val extras: Bundle = intent.extras ?: return
            val status: Status? = extras[SmsRetriever.EXTRA_STATUS] as Status?
            when (status?.statusCode) {
                CommonStatusCodes.SUCCESS -> {
                    val message: String =
                        extras[SmsRetriever.EXTRA_SMS_MESSAGE] as String? ?: return
                    val otpCode = "[0-9]{6}".toRegex().find(message)?.value ?: return
                    otpCodeSubject.onNext(otpCode)

                    Timber.i("success: $message. OTP: $otpCode")
                }
                CommonStatusCodes.TIMEOUT -> {
                    Timber.i("timeout")
                }
            }
        }
    }

    companion object {
        private val otpCodeSubject = BehaviorSubject.create<String>()
        val otpCode: Flowable<String> = otpCodeSubject.toFlowable(BackpressureStrategy.DROP).delay(1500L, TimeUnit.MILLISECONDS)
    }
}