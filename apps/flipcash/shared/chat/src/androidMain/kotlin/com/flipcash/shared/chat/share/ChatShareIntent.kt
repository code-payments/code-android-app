package com.flipcash.shared.chat.share

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import com.flipcash.app.blob.UriPickedPhoto
import com.flipcash.app.blob.toUri

/**
 * [parseChatShare] for a received [Intent]: the share intake, the one part of sharing that needs
 * Android. Streams are read through [resolver], which is also how their types are asked for.
 */
@Suppress("DEPRECATION")
fun Intent.toChatShareRequest(resolver: ContentResolver): ChatShareRequest? {
    val streams = when (action) {
        Intent.ACTION_SEND_MULTIPLE ->
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        else -> listOfNotNull(getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
    }
    return parseChatShare(
        isShare = action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE,
        text = getCharSequenceExtra(Intent.EXTRA_TEXT),
        streams = streams.map { UriPickedPhoto(it, resolver) },
        shortcutId = getStringExtra(Intent.EXTRA_SHORTCUT_ID),
        mimeOf = { resolver.getType(it.toUri()) },
    )
}
