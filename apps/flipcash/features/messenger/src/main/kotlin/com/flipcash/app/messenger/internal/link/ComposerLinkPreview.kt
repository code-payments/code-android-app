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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
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
import java.net.URI
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
 * [state] is what the composer's card draws.
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
    private val _state = MutableStateFlow<ComposerLinkPreviewState?>(null)
    val state: StateFlow<ComposerLinkPreviewState?> = _state.asStateFlow()

    // The link the × was tapped on. It stays dismissed only while it is the draft's link, so editing
    // the link brings a preview back.
    private val dismissed = MutableStateFlow<String?>(null)

    /** Follows [draft] for [chatId] until [scope] ends, keeping at most one preview ready. */
    @OptIn(FlowPreview::class)
    fun bind(scope: CoroutineScope, chatId: ChatId, draft: Flow<String>) {
        scope.launch {
            try {
                val links = draft
                    .debounce(DEBOUNCE)
                    .map(::firstWebUrl)
                    .distinctUntilChanged()
                    .onEach { url -> if (url != dismissed.value) dismissed.value = null }
                combine(links, dismissed) { url, gone -> url?.takeIf { it != gone } }
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
        val preview = ready.getAndSet(null)
        dropCard()
        if (preview == null) return null
        if (text.contains(preview.url)) return preview
        preview.imageUploadId?.let(images::discard)
        return null
    }

    /** Drops the ready preview and its staged image, for a draft that won't send it. */
    fun clear() {
        ready.getAndSet(null)?.imageUploadId?.let(images::discard)
        dropCard()
    }

    /** The ×: removes the preview while its link stays in the draft. */
    fun dismiss() {
        val url = _state.value?.url ?: return
        clear()
        dismissed.value = url
    }

    private fun dropCard() {
        _state.value = null
    }

    /**
     * The link to preview, only when the send could carry it. The detector lowercases a scheme the
     * keyboard capitalised ("Https://"), and the server matches the preview's url against the text
     * byte for byte, so such a link would show a card that is then dropped at send.
     */
    private fun firstWebUrl(text: String): String? =
        (classifier.firstCard(detectUrls(text)) as? LinkCard.Web)?.url
            ?.takeIf { LinkPreviewChecks.wholeLink(text, it) }

    private suspend fun prepare(scope: CoroutineScope, chatId: ChatId, url: String) {
        val host = hostOf(url)
        _state.value = ComposerLinkPreviewState.Loading(url, host)
        val page = lookup(url).getOrNull() as? LinkCard.Web.State.Resolved
        val title = page?.let { LinkPreviewChecks.cleanTitle(it.title) }
        if (page == null || title == null) {
            _state.value = null
            return
        }
        val description = LinkPreviewChecks.cleanDescription(page.description.orEmpty())
        var imageId: String? = null
        try {
            val file = page.imageUrl?.let { download(it) }
            imageId = file?.let { uploads.stage(chatId, Uri.fromFile(it)) }
            ready.set(OutgoingLinkPreview(url, title, description, imageId))
            _state.value = ComposerLinkPreviewState.Ready(
                url = url,
                host = host,
                title = title,
                description = description.ifEmpty { null },
                image = file?.takeIf { imageId != null },
                imageUploading = imageId != null,
            )
            if (file != null && imageId != null) {
                val id = imageId
                scope.launch { followUpload(id) }
                scope.launch { deleteWhenUnused(id, file) }
            }
        } catch (e: CancellationException) {
            // The draft moved on while the image was staging; nothing will send it.
            imageId?.let(images::discard)
            throw e
        }
    }

    /** Clears the card's spinner once [id] is up, and drops the image if its upload fails. */
    private suspend fun followUpload(id: String) {
        val settled = uploads.state(id).first {
            it == null || it is ChatMediaUploadState.Uploaded || it is ChatMediaUploadState.Processing ||
                it is ChatMediaUploadState.Failed
        }
        val failed = settled == null || settled is ChatMediaUploadState.Failed
        if (failed) {
            // Only while the draft still owns the image: once taken, the send handles its failure.
            val owned = ready.get()?.takeIf { it.imageUploadId == id }
            if (owned != null && ready.compareAndSet(owned, owned.copy(imageUploadId = null))) {
                images.discard(id)
            }
        }
        _state.update { current ->
            (current as? ComposerLinkPreviewState.Ready)
                ?.takeIf { it.imageUploading }
                ?.copy(imageUploading = false, image = current.image.takeUnless { failed })
                ?: current
        }
    }

    /**
     * Deletes the downloaded [file] once the upload holds its own encoded copy, which it does past
     * preparing, and the card no longer draws it.
     */
    private suspend fun deleteWhenUnused(id: String, file: File) {
        try {
            uploads.state(id).first { it !is ChatMediaUploadState.Preparing }
            _state.first { (it as? ComposerLinkPreviewState.Ready)?.image != file }
        } finally {
            withContext(NonCancellable) { file.delete() }
        }
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

    internal companion object {
        val DEBOUNCE = 400.milliseconds
        private const val DIRECTORY = "link-preview-images"
    }
}

/** What the composer's link card shows. */
internal sealed interface ComposerLinkPreviewState {
    val url: String
    val host: String

    /** The page is being fetched. */
    data class Loading(override val url: String, override val host: String) : ComposerLinkPreviewState

    /** The preview as the recipient will see it. [image] is a local file, absent when the page has none or its upload failed. */
    data class Ready(
        override val url: String,
        override val host: String,
        val title: String,
        val description: String?,
        val image: File?,
        val imageUploading: Boolean,
    ) : ComposerLinkPreviewState
}

/** The link's host without a leading `www.`, as the card's first line shows it. */
internal fun hostOf(url: String): String =
    (runCatching { URI(url).host }.getOrNull() ?: url).removePrefix("www.")
