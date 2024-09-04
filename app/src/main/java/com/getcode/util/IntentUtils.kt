package com.getcode.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.getcode.BuildConfig
import com.getcode.utils.makeE164


object IntentUtils {

    fun appSettings() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    fun sendSms(phoneValue: String, message: String) = Intent(
        Intent.ACTION_SENDTO,
        Uri.parse("smsto:${phoneValue.makeE164()}")
    ).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
        putExtra("sms_body", message)
    }

    fun tweet(message: String) = Intent(Intent.ACTION_VIEW).apply {
        val url = Linkify.tweet(message)
        setData(Uri.parse(url))
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    fun share(text: String): Intent {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        shareIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        return shareIntent
    }

    fun tipCard(username: String, platform: String): Intent {
        val url = Linkify.tipCard(username, platform)

        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, url)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, null).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return shareIntent
    }

    fun cashLink(
        entropy: String,
        formattedAmount: String,
    ): Intent {
        val url = Linkify.cashLink(entropy)
        val text = "$formattedAmount $url"

        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return shareIntent
    }
}
