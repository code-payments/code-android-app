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

/**
 * Takes the shared image out of [intent], leaving nothing behind that the rest of the app could
 * act on.
 *
 * Returns what [sharedImageUri] returns, and additionally strips two things from [intent] itself.
 * Both strips run for any share, including one carrying no image at all, because the sender
 * chooses what the intent holds:
 *
 * - **`data`.** The share filters are the only ones on `MainActivity` that don't pin a scheme and
 *   host; a filter naming just a mime type accepts `content:` and `file:`. So a sender can hang an
 *   arbitrary `Uri` off an otherwise ordinary image share, and the deeplink listener reads
 *   `intent.data` and routes it. That route is tapped-link handling, which is allowed to open
 *   `/login` — the thing the scan path refuses via `DeeplinkType.isScannable`. Clearing `data`
 *   stops a share reaching it at all.
 * - **`EXTRA_STREAM`.** `MainActivity` is `singleTask`, so this intent becomes the task's own and
 *   is redelivered to `onCreate` on every restore from recents after a process death. Without the
 *   strip, the same image is scanned again each time, long after the user shared it.
 *
 * Apply this before anything else reads the intent — in particular before `super.onNewIntent`,
 * which is where the deeplink listener runs.
 */
fun consumeSharedImage(intent: Intent): Uri? {
    if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) {
        return null
    }

    val uri = sharedImageUri(intent)
    // Not `data = null`: that clears the type too, and the type is what `sharedImageUri` keys off.
    intent.setDataAndType(null, intent.type)
    intent.removeExtra(Intent.EXTRA_STREAM)
    return uri
}
