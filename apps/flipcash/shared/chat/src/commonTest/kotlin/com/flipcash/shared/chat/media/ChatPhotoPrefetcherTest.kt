package com.flipcash.shared.chat.media

import com.flipcash.app.core.media.ImageCachePresence
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.chat.MessageEncryption
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.ImageMetadata
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.MediaItemRendition
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.user.UserManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ChatPhotoPrefetcherTest {
    private val chatId = ChatId(byteArrayOf(1))
    private val self = listOf<Byte>(1)
    private val peer = listOf<Byte>(2)
    private val now = Instant.fromEpochSeconds(10_000)

    private val original = rendition(MediaItemRendition.Role.ORIGINAL, 0x0a, sizeBytes = 4_000_000)
    private val display = rendition(MediaItemRendition.Role.DISPLAY, 0x0b, sizeBytes = 200_000)

    private val chatRow = MutableStateFlow<ChatMetadataEntity?>(null)
    private val messages = mockk<ChatMessageDataSource>()
    private val metadata = mockk<ChatMetadataDataSource> { every { observeById(chatId) } returns chatRow }
    private val members = mockk<ChatMemberDataSource>()
    private val user = mockk<UserManager> { every { accountId } returns self }
    private val cache = mockk<ImageCachePresence> { coEvery { holds(any()) } returns false }
    private val platform = mockk<PhotoPrefetchPlatform> {
        every { network() } returns PrefetchNetwork(connected = true, dataSaver = false, roaming = false)
        coEvery { download(any()) } returns PhotoPrefetchResult.Cached(bytes = 200_000)
    }

    /** Stands in for the bubble's pick: the derived display rendition when there is one. */
    private val target = PhotoPrefetchTarget { item, _ ->
        item.renditions.firstOrNull { it.role == MediaItemRendition.Role.DISPLAY } ?: item.renditions.firstOrNull()
    }

    private fun rendition(role: MediaItemRendition.Role, id: Byte, sizeBytes: Long?) = MediaItemRendition(
        role = role,
        blobId = BlobId(byteArrayOf(id)),
        blob = sizeBytes?.let {
            BlobMetadata(
                mimeType = "image/jpeg", sizeBytes = it, downloadUrl = "https://cdn.example/$id",
                expiresAtMillis = null, image = ImageMetadata(10, 10, ""),
            )
        },
    )

    private fun photoMessage(vararg renditions: MediaItemRendition, sealed: Boolean = false) = ChatMessage(
        messageId = 7,
        senderId = peer,
        content = listOf(MessageContent.Media(items = listOf(MediaItem(renditions.toList())), caption = null)),
        timestamp = now,
        unreadSeq = 0,
        encryption = if (sealed) mockk<MessageEncryption.Decrypted>() else null,
    )

    private fun chat(muted: Boolean = false) = ChatMetadataEntity(
        chatIdHex = "01",
        chatType = ChatType.CONTACT_DM.name,
        lastActivityEpochMs = 0,
        lastMessageId = 7,
        muteForever = muted,
    )

    private fun prefetcher(scope: CoroutineScope = mockk()) = ChatPhotoPrefetcher(
        messages, metadata, members, user, target, platform, cache,
        scope = scope, readyTimeout = 10.seconds, now = { now },
    )

    private fun expected(rendition: MediaItemRendition, sealed: Boolean = false) =
        ChatPhoto(chatId, rendition, senderId = peer, sealed = sealed, redacted = false)

    @Test
    fun `a photo whose chat is stored by the running refresh is downloaded once it is`() = runTest {
        coEvery { messages.getMessage(chatId, 7) } returns photoMessage(original, display)

        val job = launch { prefetcher().prefetch(PhotoPrefetchTrigger.Push, chatId, 7, inline = null, pushedMuted = false) }
        advanceTimeBy(5.seconds)
        coVerify(exactly = 0) { platform.download(any()) }

        chatRow.value = chat()
        job.join()
        coVerify(exactly = 1) { platform.download(expected(display)) }
    }

    @Test
    fun `a chat that is never stored drops the prefetch`() = runTest {
        coEvery { messages.getMessage(chatId, 7) } returns photoMessage(original, display)

        prefetcher().prefetch(PhotoPrefetchTrigger.Stream, chatId, 7, inline = null, pushedMuted = false)

        coVerify(exactly = 0) { platform.download(any()) }
    }

    @Test
    fun `a pushed plaintext message is read from the push while its row is not stored`() = runTest {
        coEvery { messages.getMessage(chatId, 7) } returns null
        chatRow.value = chat()

        prefetcher().prefetch(PhotoPrefetchTrigger.Push, chatId, 7, photoMessage(original), pushedMuted = false)

        coVerify(exactly = 1) { platform.download(expected(original)) }
    }

    @Test
    fun `a photo in a chat muted on this device is not downloaded`() = runTest {
        coEvery { messages.getMessage(chatId, 7) } returns photoMessage(original, display)
        chatRow.value = chat(muted = true)

        prefetcher().prefetch(PhotoPrefetchTrigger.Stream, chatId, 7, inline = null, pushedMuted = false)

        coVerify(exactly = 0) { platform.download(any()) }
    }

    @Test
    fun `a sealed photo with no declared size is not downloaded`() = runTest {
        val unsized = rendition(MediaItemRendition.Role.ORIGINAL, 0x0c, sizeBytes = null)
        coEvery { messages.getMessage(chatId, 7) } returns photoMessage(unsized, sealed = true)
        chatRow.value = chat()

        prefetcher().prefetch(PhotoPrefetchTrigger.Push, chatId, 7, inline = null, pushedMuted = false)

        coVerify(exactly = 0) { platform.download(any()) }
    }

    @Test
    fun `a failed read for a stream arrival is logged, not thrown into the app`() = runTest {
        coEvery { messages.getMessage(chatId, 7) } throws IllegalStateException("database closed")
        var uncaught: Throwable? = null
        val scope = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(testScheduler) + CoroutineExceptionHandler { _, e -> uncaught = e },
        )

        prefetcher(scope).arrived(chatId, 7)
        advanceUntilIdle()

        assertNull(uncaught)
    }

    @Test
    fun `a blob already downloading is not downloaded again when the stream reports it too`() = runTest {
        coEvery { messages.getMessage(chatId, 7) } returns photoMessage(original, display)
        chatRow.value = chat()
        val gate = CompletableDeferred<PhotoPrefetchResult>()
        coEvery { platform.download(any()) } coAnswers { gate.await() }
        val prefetcher = prefetcher()

        val push = launch { prefetcher.prefetch(PhotoPrefetchTrigger.Push, chatId, 7, inline = null, pushedMuted = false) }
        val stream = launch { prefetcher.prefetch(PhotoPrefetchTrigger.Stream, chatId, 7, inline = null, pushedMuted = false) }
        runCurrent()
        gate.complete(PhotoPrefetchResult.Cached(bytes = 200_000))
        joinAll(push, stream)

        coVerify(exactly = 1) { platform.download(any()) }
    }
}
