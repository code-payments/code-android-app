package com.flipcash.shared.chat.media

import com.flipcash.app.blob.BlobStorageCoordinator
import com.flipcash.app.blob.BlurHashEncoder
import com.flipcash.app.blob.ChatMediaEncoder
import com.flipcash.app.blob.ChatMediaLimits
import com.flipcash.app.blob.ChatMediaRetry
import com.flipcash.app.blob.FilePickedPhoto
import com.flipcash.app.blob.ImageCodec
import com.flipcash.app.blob.PickedPhoto
import com.flipcash.app.blob.PickedPhotos
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.internal.OutgoingEncryption
import com.flipcash.shared.chat.internal.sealing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A photo that is in storage: what a media message needs to reference it. */
data class UploadedPhoto(
    val blobId: BlobId,
    val width: Int,
    val height: Int,
    /** Only computed for a sealed photo, whose preview the server can't derive. */
    val blurhash: String?,
    /** Length of the stripped JPEG, before any sealing overhead. */
    val sizeBytes: Long,
    /** The chat the bytes were sealed for, or null if they went up plain. */
    val sealedFor: ChatId?,
)

/** Where one staged photo is, from picking it to having it ready to post. */
sealed interface ChatMediaUploadState {
    /** Being encoded, or waiting for a store attempt to move bytes; every attempt starts here. */
    data class Preparing(val sourceWidth: Int?, val sourceHeight: Int?) : ChatMediaUploadState

    /** Bytes are going up. [fraction] only grows within an attempt. */
    data class Uploading(val fraction: Float) : ChatMediaUploadState

    /** Stored; waiting on the server to moderate and finalize it. */
    data class Processing(val photo: UploadedPhoto) : ChatMediaUploadState

    data class Uploaded(val photo: UploadedPhoto) : ChatMediaUploadState

    /**
     * [retryable] is whether [ChatMediaUploads.retry] can help. [stored] is set when the bytes did
     * reach storage, so a retry polls rather than uploads.
     */
    data class Failed(
        val cause: Throwable,
        val retryable: Boolean,
        val stored: UploadedPhoto? = null,
    ) : ChatMediaUploadState
}

/**
 * Uploads chat photos as they are staged, before the viewer sends them, so sending is mostly
 * posting. App-scoped: a staged photo keeps uploading when the chat screen is left.
 *
 * Each photo is encoded once and written to `pending-media/<id>.jpg` under the app's files directory; every later attempt
 * resends those bytes. In a chat that encrypts the photo is sealed and a failure to do so fails the
 * chip, never falls back to plain bytes.
 *
 * The id [stage] returns is the chip id: it keys [state], [remove] and [retry], and is what
 * `ChatCoordinator.sendMedia` takes.
 */
@Singleton
class ChatMediaUploads internal constructor(
    private val directory: () -> File,
    private val encoder: ChatMediaEncoder,
    private val blobs: BlobStorageCoordinator,
    private val outgoing: OutgoingEncryption,
    private val sourceSize: (PickedPhoto) -> Pair<Int, Int>?,
    private val blurhashOf: (ByteArray) -> String?,
    private val photos: PickedPhotos,
    private val scope: CoroutineScope,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val keepImage: (cacheKey: String, file: File) -> Unit = { _, _ -> },
) {
    @Inject
    internal constructor(
        location: ChatMediaLocation,
        encoder: ChatMediaEncoder,
        blobs: BlobStorageCoordinator,
        outgoing: OutgoingEncryption,
        codec: ImageCodec,
        imageCache: SentImageCache,
        photos: PickedPhotos,
        dispatchers: DispatcherProvider,
    ) : this(
        directory = { location.directory },
        encoder = encoder,
        blobs = blobs,
        outgoing = outgoing,
        sourceSize = { photo -> codec.sourceSize(photo)?.let { it.width to it.height } },
        blurhashOf = { bytes -> BlurHashEncoder.fromJpeg(codec, bytes) },
        photos = photos,
        scope = CoroutineScope(SupervisorJob() + dispatchers.IO),
        keepImage = { key, file -> imageCache.keep(key, file) },
    )

    private class Entry(
        val id: String,
        val chatId: ChatId,
        val photo: PickedPhoto?,
        val fileName: String,
        /** Completes true once [photo] has been written, for a camera shot staged at the shutter. */
        val ready: Deferred<Boolean>? = null,
    ) {
        @Volatile var job: Job? = null
        @Volatile var source: Pair<Int, Int>? = null
        @Volatile var encoded: Encoded? = null
    }

    private class Encoded(val width: Int, val height: Int, val blurhash: String?)

    private val entries = HashMap<String, Entry>()
    private val states = MutableStateFlow<Map<String, ChatMediaUploadState>>(emptyMap())

    /**
     * Starts preparing and uploading [photo] for [chatId]. Returns the chip id.
     *
     * With [ready], the chip exists at once but reading [photo] waits for it: the camera stages its
     * shot at the shutter, before the file is written. Completing it false leaves the chip
     * preparing, for the caller to [remove].
     */
    fun stage(chatId: ChatId, photo: PickedPhoto, ready: Deferred<Boolean>? = null): String {
        val id = newId()
        val entry = Entry(id, chatId, photo, "$id.jpg", ready)
        synchronized(entries) { entries[id] = entry }
        set(id, ChatMediaUploadState.Preparing(null, null))
        launch(entry)
        return id
    }

    /**
     * Re-creates the chip of a message queued by an earlier process, from the record that survived
     * it: [stored] set resumes at polling ([retry] to go), unset resumes at uploading the file.
     * A no-op if [id] is already known.
     */
    fun restore(
        id: String,
        chatId: ChatId,
        fileName: String,
        width: Int,
        height: Int,
        blurhash: String?,
        stored: UploadedPhoto?,
    ) {
        val entry = synchronized(entries) {
            if (id in entries) return
            Entry(id, chatId, null, fileName).also {
                it.encoded = Encoded(width, height, blurhash)
                entries[id] = it
            }
        }
        // Failed either way: with [stored] set, a retry polls it, and nothing runs until asked to.
        set(entry.id, ChatMediaUploadState.Failed(IllegalStateException("Interrupted"), retryable = true, stored = stored))
    }

    /**
     * [stage] for a photo named by a platform location, such as an Android content URI string.
     * See [PickedPhotos].
     */
    fun stage(chatId: ChatId, location: String, ready: Deferred<Boolean>? = null): String =
        stage(chatId, photos.fromLocation(location), ready)

    /** [stage] for a photo in a file, such as a camera shot. */
    fun stage(chatId: ChatId, file: File, ready: Deferred<Boolean>? = null): String =
        stage(chatId, FilePickedPhoto(file), ready)

    /** The file of a photo staged as [fileName]. */
    fun file(fileName: String): File = File(directory(), fileName)

    fun hasFile(fileName: String): Boolean = file(fileName).isFile

    fun has(id: String): Boolean = synchronized(entries) { id in entries }

    /** The chip's state, null once it is removed. */
    fun state(id: String): Flow<ChatMediaUploadState?> = states.map { it[id] }.distinctUntilChanged()

    /** Every chip's state at once, for a view of many. */
    val allStates: Flow<Map<String, ChatMediaUploadState>> get() = states

    fun current(id: String): ChatMediaUploadState? = states.value[id]

    /** The photo's pixel size: the encoded size once known, the source's before that. */
    fun pixelSize(id: String): Pair<Int, Int>? {
        val entry = synchronized(entries) { entries[id] } ?: return null
        return entry.encoded?.let { it.width to it.height } ?: entry.source
    }

    /** Suspends until the chip is uploaded or failed; null if it is removed first. */
    suspend fun awaitSettled(id: String): ChatMediaUploadState? =
        state(id).first { it == null || it is ChatMediaUploadState.Uploaded || it is ChatMediaUploadState.Failed }

    /**
     * Cancels the chip's work and deletes its file. With [keepAs], the file is first copied into the
     * image disk cache under that key, so the sent message draws the photo straight from it rather
     * than fetching what was just uploaded.
     */
    fun remove(id: String, keepAs: String? = null) {
        val entry = synchronized(entries) { entries.remove(id) }
        if (entry != null) {
            entry.job?.cancel()
            states.update { it - id }
        }
        if (keepAs == null) SentPhotoPreviews.drop(id)
        // An entry not tracked in this process (a queue entry dropped at launch): the file is still ours.
        val file = entry?.let(::file) ?: file("$id.jpg")
        scope.launch {
            if (keepAs != null && file.isFile) runCatching { keepImage(keepAs, file) }
            runCatching { file.delete() }
        }
    }

    /**
     * Resumes a failed chip: a stored photo is polled again, one that never reached storage is
     * uploaded again from the file written the first time.
     */
    fun retry(id: String) {
        val entry = synchronized(entries) { entries[id] } ?: return
        if (entry.job?.isActive == true) return
        if (states.value[id] is ChatMediaUploadState.Uploaded) return
        val stored = when (val s = states.value[id]) {
            is ChatMediaUploadState.Failed -> s.stored
            is ChatMediaUploadState.Processing -> s.photo
            else -> null
        }
        set(id, stored?.let { ChatMediaUploadState.Processing(it) } ?: ChatMediaUploadState.Preparing(entry.source?.first, entry.source?.second))
        launch(entry)
    }

    private fun launch(entry: Entry) {
        entry.job = scope.launch { run(entry) }
    }

    private fun file(entry: Entry) = File(directory(), entry.fileName)

    /** Writes the chip's state unless it was removed meanwhile, so a late write can't resurrect it. */
    private fun set(id: String, state: ChatMediaUploadState) {
        if (has(id)) states.update { it + (id to state) }
    }

    private suspend fun run(entry: Entry) {
        try {
            if (entry.ready?.await() == false) return
            val stored = (states.value[entry.id] as? ChatMediaUploadState.Processing)?.photo
                ?: store(entry)
                ?: return
            set(entry.id, ChatMediaUploadState.Processing(stored))
            blobs.awaitChatMediaReady(stored.blobId).fold(
                onSuccess = { set(entry.id, ChatMediaUploadState.Uploaded(stored)) },
                onFailure = { fail(entry, it, stored) },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            fail(entry, e, null)
        }
    }

    /** Encodes (once), then stores. Null if it failed, with the chip already marked. */
    private suspend fun store(entry: Entry): UploadedPhoto? {
        if (entry.photo != null && entry.source == null) {
            entry.source = sourceSize(entry.photo)
        }
        set(entry.id, ChatMediaUploadState.Preparing(entry.source?.first, entry.source?.second))

        val sealer = outgoing.blobSealer(entry.chatId).getOrElse { return fail(entry, it) }

        if (entry.encoded == null) {
            val photo = entry.photo ?: return fail(entry, IllegalStateException("Nothing to encode"), retryable = false)
            val policy = blobs.policy.first()
                ?: blobs.preloadPolicy().getOrElse { return fail(entry, it) }
            val limits = (if (sealer != null) ChatMediaLimits.sealed(policy) else ChatMediaLimits.plain(policy))
                .getOrElse { return fail(entry, it) }
            val encoded = encoder.encode(photo, limits).getOrElse { return fail(entry, it) }

            runCatching {
                val file = file(entry)
                file.parentFile?.mkdirs()
                file.writeBytes(encoded.bytes)
            }.onFailure { return fail(entry, it) }
            entry.encoded = Encoded(
                width = encoded.width,
                height = encoded.height,
                blurhash = if (sealer != null) blurhashOf(encoded.bytes) else null,
            )
        }

        val encoded = entry.encoded!!
        val bytes = runCatching { file(entry).readBytes() }.getOrElse { return fail(entry, it, retryable = false) }
        SentPhotoPreviews.stage(entry.id, bytes)

        var storing = true
        val blobId = blobs.storeChatMediaUnfinalized(
            jpeg = bytes,
            sealing = sealer?.sealing(bytes),
            onAttempt = { set(entry.id, ChatMediaUploadState.Preparing(entry.source?.first, entry.source?.second)) },
            onProgress = { sent, total -> if (storing) progress(entry.id, sent, total) },
        ).also { storing = false }
            .getOrElse { return fail(entry, it) }

        return UploadedPhoto(
            blobId = blobId,
            width = encoded.width,
            height = encoded.height,
            blurhash = encoded.blurhash,
            sizeBytes = bytes.size.toLong(),
            sealedFor = sealer?.chatId,
        )
    }

    /** Monotonic within an attempt; an update with no usable length says nothing about progress. */
    private fun progress(id: String, sent: Long, total: Long) {
        if (total <= 0 || sent < 0) return
        val fraction = (sent.toDouble() / total).toFloat().coerceIn(0f, 1f)
        states.update { all ->
            when (val current = all[id]) {
                is ChatMediaUploadState.Preparing -> all + (id to ChatMediaUploadState.Uploading(fraction))
                is ChatMediaUploadState.Uploading ->
                    if (fraction > current.fraction) all + (id to ChatMediaUploadState.Uploading(fraction)) else all
                else -> all
            }
        }
    }

    private fun fail(entry: Entry, cause: Throwable, stored: UploadedPhoto? = null, retryable: Boolean? = null): Nothing? {
        // A chip removed while its work was in flight stays removed.
        if (has(entry.id)) {
            states.update {
                it + (entry.id to ChatMediaUploadState.Failed(cause, retryable ?: ChatMediaRetry.isRetryable(cause), stored))
            }
        }
        return null
    }

    companion object {
        const val DIRECTORY = "pending-media"

        /** The photo's file in [filesDir], where `sendMedia`'s pending row points the UI. */
        fun fileFor(filesDir: File, fileName: String) = File(File(filesDir, DIRECTORY), fileName)
    }
}
