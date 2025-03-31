package xyz.flipchat.app.util

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import xyz.flipchat.app.BuildConfig


object IntentUtils {

    fun appSettings() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    fun dialNumber(number: String) = Intent(Intent.ACTION_DIAL).apply {
        data = Uri.parse("tel:$number")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    fun shareRoom(roomNumber: Long): Intent {
        val shareLink = "https://app.flipchat.xyz/room/$roomNumber"

        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareLink)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, null).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return shareIntent
    }
}
