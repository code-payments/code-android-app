package com.flipcash.shared.chat.ui.media

import android.content.Context
import android.content.Intent
import com.flipcash.app.blob.FilePickedPhoto
import com.flipcash.app.blob.toUri
import com.flipcash.shared.chat.share.PendingChatShare
import com.flipcash.shared.chat.share.PendingChatShares
import com.flipcash.shared.chat.share.toChatShareRequest

/**
 * Takes a share intent into [shares]. Returns false when [intent] is not a share we can stage.
 *
 * The images are copied into the capture directory before this returns, while the intent's read
 * grant is certain to be held: it is scoped to the receiving activity and does not outlive it, and
 * the picker, the navigation and a process restart all sit between here and the composer. Only the
 * first [MAX_STAGED_PHOTOS] are copied, since the composer would refuse the rest.
 */
suspend fun receiveChatShare(context: Context, intent: Intent, shares: PendingChatShares): Boolean {
    val staged = try {
        stageChatShare(context, intent, shares)
    } catch (t: Throwable) {
        shares.cancelIncoming()
        throw t
    }
    // Whatever the outcome, nobody should keep waiting for a share that is not coming.
    if (!staged) shares.cancelIncoming()
    return staged
}

private suspend fun stageChatShare(context: Context, intent: Intent, shares: PendingChatShares): Boolean {
    val resolver = context.contentResolver
    val request = intent.toChatShareRequest(resolver)
        ?: return false
    val copies = copyDroppedImages(request.imageSources.take(MAX_STAGED_PHOTOS).map { it.toUri() }) {
        copyIntoCaptureFile(context, it)
    }
    // Images were all that was shared and none could be read: there is nothing to stage.
    if (request.text == null && copies.isEmpty()) return false
    shares.offer(
        PendingChatShare(
            text = request.text,
            images = copies.mapNotNull { uri -> uri.path?.let { FilePickedPhoto(java.io.File(it)) } },
            chatId = com.flipcash.shared.chat.share.chatIdFromShortcutId(request.shortcutId),
            droppedImageCount = (request.imageSources.size - MAX_STAGED_PHOTOS).coerceAtLeast(0),
        )
    )
    return true
}
