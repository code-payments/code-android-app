package com.flipcash.app.core.android

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.flipcash.app.core.util.Linkify
import java.io.File


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

    fun imageSearch(query: String) = Intent(Intent.ACTION_SEARCH).apply {
        type = "image/*"
        putExtra(SearchManager.QUERY, query)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    fun photosApp() = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
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

    fun emailApp() = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_APP_EMAIL)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    fun appStoreListing(packageName: String) = Intent(Intent.ACTION_VIEW).apply {
        setData(Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    fun shareFile(context: Context, file: File, mimeType: String): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            type = mimeType
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, null).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
