package com.flipcash.shared.chat.share

import com.flipcash.app.blob.FilePickedPhoto
import com.flipcash.app.blob.PickedPhoto
import androidx.compose.runtime.staticCompositionLocalOf
import com.flipcash.services.models.chat.ChatId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What another app handed us through the system share sheet, as it arrived: the text and the
 * handles of the images, before anything is copied. The URIs are readable only under the
 * grant that came with the intent, so [ChatShareRequest] is turned into a [PendingChatShare] on
 * receipt and never kept.
 *
 * @param shortcutId `Intent.EXTRA_SHORTCUT_ID`, set when the user picked one of our direct-share
 * targets. Nothing publishes those yet; see [chatIdFromShortcutId].
 */
data class ChatShareRequest(
    val text: String?,
    val imageSources: List<PickedPhoto>,
    val shortcutId: String? = null,
)

/**
 * Reads a share into a [ChatShareRequest], or null when there is nothing in it we can stage.
 *
 * [isShare] is whether the platform delivered it as a share (Android: ACTION_SEND or ACTION_SEND_MULTIPLE). A stream is taken only when [mimeOf] says
 * an image type for it: the manifest filter admits an image type, but a sender can attach anything,
 * and an unknown type is not taken. Blank text is dropped.
 */
fun parseChatShare(
    isShare: Boolean,
    text: CharSequence?,
    streams: List<PickedPhoto>,
    shortcutId: String? = null,
    mimeOf: (PickedPhoto) -> String?,
): ChatShareRequest? {
    if (!isShare) return null
    val body = text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    val images = streams.filter { runCatching { mimeOf(it) }.getOrNull()?.startsWith("image/") == true }
    if (body == null && images.isEmpty()) return null
    return ChatShareRequest(body, images, shortcutId)
}

/**
 * The seam for direct share: the conversation a sharing shortcut stands for, so the picker can be
 * skipped. Always null until shortcuts are published; the later change maps the id it chose for
 * each shortcut back to a [ChatId] here and nothing else has to move.
 */
@Suppress("UNUSED_PARAMETER")
fun chatIdFromShortcutId(shortcutId: String?): ChatId? = null

/**
 * A share ready to send: [text] for the message field and [images], which are app-owned files under
 * the capture directory, safe to read after the sender's grant is gone.
 *
 * [chatId] is the chat the sender suggested, which the picker offers first and selects; null when
 * nothing was suggested. [droppedImageCount] is how many images past the limit were left out.
 */
data class PendingChatShare(
    val text: String?,
    val images: List<PickedPhoto>,
    val chatId: ChatId? = null,
    val droppedImageCount: Int = 0,
    /** Set once the picker (or a direct target) has been opened for it, so it is opened once. */
    val presented: Boolean = false,
) {
    internal fun deleteFiles() {
        images.forEach { photo -> (photo as? FilePickedPhoto)?.let { runCatching { it.file.delete() } } }
    }
}

/**
 * The one share waiting for the picker. A holder rather than route arguments: the picker route
 * lives in the back stack and is saved and restored with it, so anything on it would be presented
 * again every time it is restored. Here, [beginPresenting] claims the share once.
 *
 * Process-scoped on purpose. A share that doesn't survive process death is dropped, which is the
 * right outcome: its files are cache and nothing was sent.
 */
@Singleton
class PendingChatShares @Inject constructor() {
    private val _pending = MutableStateFlow<PendingChatShare?>(null)
    val pending: StateFlow<PendingChatShare?> = _pending.asStateFlow()

    private val _incoming = MutableStateFlow(false)

    /**
     * True from the moment a share intent arrives until its images are copied and it is either
     * [offer]ed or given up on ([cancelIncoming]). Copying is asynchronous, so without this the
     * first screen of a share launch is chosen before [pending] is known and the app opens on its
     * ordinary start screen with the picker pushed over it a beat later.
     */
    val incoming: StateFlow<Boolean> = _incoming.asStateFlow()

    /** Marks a share as on its way in, before [pending] holds it. */
    fun expectIncoming() {
        _incoming.value = true
    }

    /** The share that was expected turned out to hold nothing to stage. */
    fun cancelIncoming() {
        _incoming.value = false
    }

    /** Holds [share], dropping (and deleting the files of) any share it replaces. */
    fun offer(share: PendingChatShare) {
        _pending.getAndUpdateCompat { share }?.deleteFiles()
        _incoming.value = false
    }

    /** Claims the share for presenting, once. Null when there is none or it was already claimed. */
    fun beginPresenting(): PendingChatShare? {
        var claimed: PendingChatShare? = null
        _pending.update { current ->
            if (current == null || current.presented) {
                claimed = null
                current
            } else {
                current.copy(presented = true).also { claimed = it }
            }
        }
        return claimed
    }

    /**
     * Lets go of the held share without deleting its files: they belong to sends already under
     * way, which read them from the app's own scope after the picker is gone.
     */
    fun release() {
        _pending.value = null
        _incoming.value = false
    }

    /** Drops the held share and deletes its files: the user backed out, or can't receive it. */
    fun discard() {
        _pending.getAndUpdateCompat { null }?.deleteFiles()
        _incoming.value = false
    }

    private inline fun MutableStateFlow<PendingChatShare?>.getAndUpdateCompat(
        transform: (PendingChatShare?) -> PendingChatShare?,
    ): PendingChatShare? {
        while (true) {
            val prev = value
            if (compareAndSet(prev, transform(prev))) return prev
        }
    }
}

val LocalPendingChatShares = staticCompositionLocalOf<PendingChatShares?> { null }
