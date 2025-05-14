package com.flipcash.app.core.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.net.toUri
import com.flipcash.app.core.util.Linkify


object IntentUtils {

    fun appSettings(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    fun tweet(message: String) = Intent(Intent.ACTION_VIEW).apply {
        val url = Linkify.tweet(message)
        setData(url.toUri())
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
