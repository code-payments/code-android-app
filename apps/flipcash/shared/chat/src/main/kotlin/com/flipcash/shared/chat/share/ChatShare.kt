package com.flipcash.shared.chat.share

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
import com.flipcash.services.models.chat.ChatId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What another app handed us through the system share sheet, as it arrived: the text and the
 * content URIs of the images, before anything is copied. The URIs are readable only under the
 * grant that came with the intent, so [ChatShareRequest] is turned into a [PendingChatShare] on
 * receipt and never kept.
 *
 * @param shortcutId `Intent.EXTRA_SHORTCUT_ID`, set when the user picked one of our direct-share
 * targets. Nothing publishes those yet; see [chatIdFromShortcutId].
 */
data class ChatShareRequest(
    val text: String?,
    val imageSources: List<Uri>,
    val shortcutId: String? = null,
)

/**
 * Reads a share into a [ChatShareRequest], or null when there is nothing in it we can stage.
 *
 * Only ACTION_SEND and ACTION_SEND_MULTIPLE are shares. A stream is taken only when [mimeOf] says
 * an image type for it: the manifest filter admits an image type, but a sender can attach anything,
 * and an unknown type is not taken. Blank text is dropped.
 */
fun parseChatShare(
    action: String?,
    text: CharSequence?,
    streams: List<Uri>,
    shortcutId: String? = null,
    mimeOf: (Uri) -> String?,
): ChatShareRequest? {
    if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null
    val body = text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    val images = streams.filter { runCatching { mimeOf(it) }.getOrNull()?.startsWith("image/") == true }
    if (body == null && images.isEmpty()) return null
    return ChatShareRequest(body, images, shortcutId)
}

/** [parseChatShare] for a received [Intent]. */
@Suppress("DEPRECATION")
fun Intent.toChatShareRequest(mimeOf: (Uri) -> String?): ChatShareRequest? {
    val streams = when (action) {
        Intent.ACTION_SEND_MULTIPLE ->
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        else -> listOfNotNull(getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
    }
    return parseChatShare(
        action = action,
        text = getCharSequenceExtra(Intent.EXTRA_TEXT),
        streams = streams,
        shortcutId = getStringExtra(Intent.EXTRA_SHORTCUT_ID),
        mimeOf = mimeOf,
    )
}

/**
 * The seam for direct share: the conversation a sharing shortcut stands for, so the picker can be
 * skipped. Always null until shortcuts are published; the later change maps the id it chose for
 * each shortcut back to a [ChatId] here and nothing else has to move.
 */
@Suppress("UNUSED_PARAMETER")
fun chatIdFromShortcutId(shortcutId: String?): ChatId? = null

/**
 * A share ready to stage: [text] for the composer and [images], which are app-owned files under
 * the capture directory, safe to read after the sender's grant is gone.
 *
 * [chatId] is where it goes, null while the user is still choosing.
 */
data class PendingChatShare(
    val text: String?,
    val images: List<Uri>,
    val chatId: ChatId? = null,
    /** Set once the picker (or a direct target) has been opened for it, so it is opened once. */
    val presented: Boolean = false,
) {
    internal fun deleteFiles() {
        images.forEach { uri -> uri.path?.let { runCatching { File(it).delete() } } }
    }
}

/**
 * The one share waiting to land in a chat. A holder rather than route arguments: the chat route
 * lives in the back stack and is saved and restored with it, so anything on it would be staged
 * again every time it is restored. Here, [take] removes the share, so it is staged at most once
 * whatever recreates the chat screen.
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

    /** Sends the held share to [chatId]; the chat claims it with [take]. False when none is held. */
    fun route(chatId: ChatId): Boolean {
        var routed = false
        _pending.update { current ->
            routed = current != null
            current?.copy(chatId = chatId)
        }
        return routed
    }

    /**
     * [route] with the message the user ended up with in the picker, which replaces the shared text.
     * A blank [text] clears it.
     */
    fun route(chatId: ChatId, text: String?): Boolean {
        var routed = false
        _pending.update { current ->
            routed = current != null
            current?.copy(chatId = chatId, text = text?.trim()?.takeIf { it.isNotEmpty() })
        }
        return routed
    }

    /**
     * Removes and returns the share routed to [chatId]. Null for any other chat, and for every
     * call after the first.
     */
    fun take(chatId: ChatId): PendingChatShare? {
        var taken: PendingChatShare? = null
        _pending.update { current ->
            if (current != null && current.chatId == chatId) {
                taken = current
                null
            } else {
                taken = null
                current
            }
        }
        return taken
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
