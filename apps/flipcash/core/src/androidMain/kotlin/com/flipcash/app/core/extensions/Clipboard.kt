package com.flipcash.app.core.extensions

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle

fun ClipboardManager.setText(text: CharSequence, label: String) {
    setPrimaryClip(ClipData.newPlainText(label, text))
}

/**
 * Copies [text] and marks the clip sensitive, so Android 13+ hides it from the clipboard preview
 * and older Android keeps it out of keyboard suggestions that honour the flag. For a value that
 * is a secret, such as an invite link, which is as good as the chat key.
 */
fun ClipboardManager.setSensitiveText(text: CharSequence, label: String) {
    val clip = ClipData.newPlainText(label, text)
    clip.description.extras = PersistableBundle().apply { putBoolean(SensitiveClipKey, true) }
    setPrimaryClip(clip)
}

/** [ClipDescription.EXTRA_IS_SENSITIVE], which the platform only declares from API 33. */
val SensitiveClipKey: String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ClipDescription.EXTRA_IS_SENSITIVE
    } else {
        "android.content.extra.IS_SENSITIVE"
    }
