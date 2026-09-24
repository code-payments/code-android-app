package com.flipcash.app.core.scanner

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/**
 * The image, if any, that an incoming intent shares into the app.
 *
 * `MainActivity` also owns `ACTION_VIEW`, arriving through dozens of `autoVerify` App Link
 * filters — a tapped `flipcash.com`/`send.flipcash.com` link. This has to stay out of that path's
 * way rather than compete with it, so it answers only for the two `SEND` actions the share-target
 * intent filters register for, and returns null for everything else, `VIEW` included.
 *
 * `SEND_MULTIPLE` is accepted, not just `SEND`, because some senders (some gallery apps, some
 * messaging clients) route even a single shared image through `SEND_MULTIPLE`. Dropping out of
 * the share sheet for those senders is worse than taking the first image and ignoring the rest —
 * scanning is a single-code action, so there is no second thing to do with the others anyway.
 */
fun sharedImageUri(intent: Intent): Uri? {
    if (intent.type?.startsWith("image/") != true) return null

    return when (intent.action) {
        Intent.ACTION_SEND ->
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)

        Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?.firstOrNull()

        else -> null
    }
}
