package com.flipcash.shared.chat.media

import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.services.chat.BlobOpenFailure
import com.flipcash.services.chat.ChatContentCrypto
import com.flipcash.services.chat.OpenedBlob
import com.flipcash.services.controllers.BlobStorageController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ImageMetadata
import com.flipcash.services.models.chat.MediaItemRendition
import com.flipcash.services.user.UserManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class ChatPhotoLoaderTest {
    private val chatId = ChatId(byteArrayOf(1))
    private val blobId = BlobId(byteArrayOf(0x0a, 0x0b))
    private val self = listOf<Byte>(1)
    private val peer = listOf<Byte>(2)
    private val now = Instant.fromEpochSeconds(10_000)

    private val storage = mockk<BlobStorageController>()
    private val crypto = mockk<ChatContentCrypto>()
    private val user = mockk<UserManager> { every { accountId } returns self }
    private val members = mockk<ChatMemberDataSource> {
        coEvery { getMembersForChat(chatId) } returns listOf(self, peer).map {
            ChatMember(it, UserProfile(displayName = "n", socialAccounts = emptyList(), phoneNumber = null, email = null), emptyList())
        }
    }
    private val loader = ChatPhotoLoader(storage, crypto, user, members) { now }

    private fun metadata(url: String, expiresAt: Instant? = null) = BlobMetadata(
        mimeType = "image/jpeg", sizeBytes = 3, downloadUrl = url, expiresAtMillis = expiresAt?.toEpochMilliseconds(),
        image = ImageMetadata(10, 10, ""),
    )

    private fun photo(url: String?, expiresAt: Instant? = null, sealed: Boolean = false, redacted: Boolean = false) = ChatPhoto(
        chatId, MediaItemRendition(MediaItemRendition.Role.ORIGINAL, blobId, url?.let { metadata(it, expiresAt) }),
        senderId = peer, sealed = sealed, redacted = redacted,
    )

    @Test
    fun `the cache key is the blob id and not the url`() {
        assertEquals("chat-media-0a0b", photo("https://a?sig=1").cacheKey)
        assertEquals(photo("https://a?sig=1").cacheKey, photo("https://a?sig=2").cacheKey)
    }

    @Test
    fun `a usable stored url is used without a mint`() = runTest {
        assertEquals("https://a", loader.resolveUrl(photo("https://a", expiresAt = now + 1.hours)))
        coVerify(exactly = 0) { storage.refreshMetadata(any(), any()) }
    }

    @Test
    fun `an expired url is minted again for the chat`() = runTest {
        coEvery { storage.refreshMetadata(listOf(blobId), BlobAccessContext.Chat(chatId)) } returns
            Result.success(mapOf(photo("x").rendition.cacheKey to metadata("https://fresh")))

        assertEquals("https://fresh", loader.resolveUrl(photo("https://old", expiresAt = now - 1.hours)))
    }

    @Test
    fun `a missing url is minted and a url that just failed is replaced`() = runTest {
        coEvery { storage.refreshMetadata(any(), any()) } returns
            Result.success(mapOf(photo("x").rendition.cacheKey to metadata("https://fresh")))

        assertEquals("https://fresh", loader.resolveUrl(photo(null)))
        assertEquals("https://fresh", loader.resolveUrl(photo("https://stale"), failedUrl = "https://stale"))
    }

    @Test
    fun `a redacted photo never resolves`() = runTest {
        assertNull(loader.resolveUrl(photo("https://a", redacted = true)))
        coVerify(exactly = 0) { storage.refreshMetadata(any(), any()) }
    }

    @Test
    fun `a plain photo is passed through`() = runTest {
        assertEquals(listOf<Byte>(1, 2), loader.open(photo("u"), byteArrayOf(1, 2)).toList())
    }

    @Test
    fun `a sealed photo is opened for the sender and declared size`() = runTest {
        coEvery { crypto.openBlob(chatId, self, peer, peer, blobId.bytes, 3, any()) } returns
            OpenedBlob.Plaintext(byteArrayOf(7, 8, 9))

        assertEquals(listOf<Byte>(7, 8, 9), loader.open(photo("u", sealed = true), byteArrayOf(1)).toList())
    }

    @Test
    fun `a sealed photo that does not open surfaces the reason`() = runTest {
        coEvery { crypto.openBlob(any(), any(), any(), any(), any(), any(), any()) } returns
            OpenedBlob.Failed(BlobOpenFailure.Authentication)

        val failure = assertFailsWith<ChatPhotoUnavailable.Open> { loader.open(photo("u", sealed = true), byteArrayOf(1)) }
        assertEquals(BlobOpenFailure.Authentication, failure.reason)
    }
}
