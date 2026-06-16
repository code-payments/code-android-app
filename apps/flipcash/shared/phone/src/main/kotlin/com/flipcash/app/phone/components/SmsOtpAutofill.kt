package com.flipcash.app.phone.components

import android.app.Activity.RESULT_OK
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.BundleCompat
import com.getcode.utils.trace
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status

@Composable
fun SmsOtpAutofill(
    otpState: TextFieldState,
    otpLength: Int,
    attempts: Int,
) {
    val context = LocalContext.current
    val currentOtpState by rememberUpdatedState(otpState)
    val currentOtpLength by rememberUpdatedState(otpLength)
    val filled = remember { mutableStateOf(false) }

    val consentLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val message = result.data?.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)
                ?: return@rememberLauncherForActivityResult
            parseAndFill(message, currentOtpLength, currentOtpState, filled)
        }
    }

    DisposableEffect(attempts) {
        filled.value = false

        val client = SmsRetriever.getClient(context)
        client.startSmsRetriever()
            .addOnSuccessListener { trace("SmsRetriever started") }
            .addOnFailureListener { trace("SmsRetriever failed to start: ${it.message}") }
        client.startSmsUserConsent(null)
            .addOnSuccessListener { trace("SmsUserConsent started") }
            .addOnFailureListener { trace("SmsUserConsent failed to start: ${it.message}") }

        val filter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != SmsRetriever.SMS_RETRIEVED_ACTION) return
                val extras: Bundle = intent.extras ?: return
                // GMS classes (Status) need the GMS class loader for deserialization
                extras.classLoader = SmsRetriever::class.java.classLoader

                val status = BundleCompat.getParcelable(extras, SmsRetriever.EXTRA_STATUS, Status::class.java)
                if (status?.statusCode != CommonStatusCodes.SUCCESS) {
                    trace("SMS retrieval failed with status: ${status?.statusCode}")
                    return
                }

                // Retriever path: SMS message directly available
                val message = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE)
                if (message != null) {
                    trace("SmsRetriever received message")
                    parseAndFill(message, currentOtpLength, currentOtpState, filled)
                    return
                }

                // User Consent path: need to launch consent dialog
                if (filled.value) return

                val consentIntent = BundleCompat.getParcelable(
                    extras,
                    SmsRetriever.EXTRA_CONSENT_INTENT,
                    Intent::class.java
                )
                if (consentIntent != null) {
                    trace("Launching SMS consent dialog")
                    try {
                        consentLauncher.launch(consentIntent)
                    } catch (e: Exception) {
                        trace("Failed to launch consent dialog: ${e.message}")
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.registerReceiver(
                receiver, filter, SmsRetriever.SEND_PERMISSION,
                null, Context.RECEIVER_EXPORTED,
            )
        } else {
            context.registerReceiver(receiver, filter, SmsRetriever.SEND_PERMISSION, null)
        }

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
}

private fun parseAndFill(
    message: String,
    otpLength: Int,
    state: TextFieldState,
    filled: androidx.compose.runtime.MutableState<Boolean>,
) {
    if (filled.value) return
    val otp = """\b(\d{$otpLength})\b""".toRegex().find(message)?.groupValues?.get(1) ?: return
    trace("Auto-filling OTP")
    state.edit { replace(0, length, otp) }
    filled.value = true
}
