package com.flipcash.app.messenger.internal.link

import android.content.Context
import android.net.Uri
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.link.LinkPreviewChecks
import com.flipcash.shared.chat.link.LinkPreviewImages
import com.flipcash.shared.chat.link.OutgoingLinkPreview
import com.flipcash.shared.chat.media.ChatMediaUploadState
import com.flipcash.shared.chat.media.ChatMediaUploads
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.ui.detectUrls
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Builds the preview for the first web link in the draft, so the sender, not each receiver, fetches
 * the page. The page's image is downloaded through the guarded image client and staged as a chat
 * photo as soon as the preview loads, so by send time it is usually already up.
 *
 * The staged image is not a composer chip: chips come from the composer's own staged photos.
 */
internal class ComposerLinkPreview @Inject constructor(
    private val classifier: LinkCardClassifier,
    private val lookup: WebLinkLookup,
    @WebPreviewImages private val imageClient: OkHttpClient,
    private val uploads: ChatMediaUploads,
    private val images: LinkPreviewImages,
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {
    private val ready = AtomicReference<OutgoingLinkPreview?>(null)

    /** Follows [draft] for [chatId] until [scope] ends, keeping at most one preview ready. */
    @OptIn(FlowPreview::class)
    fun bind(scope: CoroutineScope, chatId: ChatId, draft: Flow<String>) {
        scope.launch {
            try {
                draft
                    .debounce(DEBOUNCE)
                    .map(::firstWebUrl)
                    .distinctUntilChanged()
                    .collectLatest { url ->
                        clear()
                        if (url != null) prepare(scope, chatId, url)
                    }
            } finally {
                clear()
            }
        }
    }

    /**
     * Hands over the ready preview for [text] and forgets it. Null when none is ready or its link is
     * no longer in the text; the send makes the authoritative whole-link check on the wire text.
     */
    fun take(text: String): OutgoingLinkPreview? {
        val preview = ready.getAndSet(null) ?: return null
        if (text.contains(preview.url)) return preview
        preview.imageUploadId?.let(images::discard)
        return null
    }

    /** Drops the ready preview and its staged image, for a draft that won't send it. */
    fun clear() {
        ready.getAndSet(null)?.imageUploadId?.let(images::discard)
    }

    private fun firstWebUrl(text: String): String? =
        (classifier.firstCard(detectUrls(text)) as? LinkCard.Web)?.url

    private suspend fun prepare(scope: CoroutineScope, chatId: ChatId, url: String) {
        val page = lookup(url).getOrNull() as? LinkCard.Web.State.Resolved ?: return
        val title = LinkPreviewChecks.cleanTitle(page.title) ?: return
        val description = LinkPreviewChecks.cleanDescription(page.description.orEmpty())
        var imageId: String? = null
        try {
            imageId = page.imageUrl?.let { stageImage(scope, chatId, it) }
            ready.set(OutgoingLinkPreview(url, title, description, imageId))
        } catch (e: CancellationException) {
            // The draft moved on while the image was staging; nothing will send it.
            imageId?.let(images::discard)
            throw e
        }
    }

    /** Downloads [imageUrl] and stages it; null if the download fails. */
    private suspend fun stageImage(scope: CoroutineScope, chatId: ChatId, imageUrl: String): String? {
        val file = download(imageUrl) ?: return null
        val id = uploads.stage(chatId, Uri.fromFile(file))
        // Past preparing, the uploads hold their own encoded copy.
        scope.launch {
            try {
                uploads.state(id).first { it !is ChatMediaUploadState.Preparing }
            } finally {
                file.delete()
            }
        }
        return id
    }

    private suspend fun download(imageUrl: String): File? = withContext(dispatchers.IO) {
        val file = File(directory(), UUID.randomUUID().toString())
        runCatching {
            imageClient.newCall(Request.Builder().url(imageUrl).build()).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                file.outputStream().use { out -> response.body.byteStream().copyTo(out) }
                file
            }
        }.onFailure { if (it is CancellationException) throw it }
            .getOrNull()
            ?: run { file.delete(); null }
    }

    private fun directory() = File(context.cacheDir, DIRECTORY).apply { mkdirs() }

    private companion object {
        val DEBOUNCE = 400.milliseconds
        const val DIRECTORY = "link-preview-images"
    }
}
