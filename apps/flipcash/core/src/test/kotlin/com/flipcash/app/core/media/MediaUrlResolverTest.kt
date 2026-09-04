package com.flipcash.app.core.media

import com.flipcash.app.core.time.FakeTimeProvider
import com.flipcash.services.controllers.BlobStorageController
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobMetadata
import com.flipcash.services.models.chat.ImageMetadata
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.MediaItemRendition
import com.getcode.utils.base58
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class MediaUrlResolverTest {

    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val time = FakeTimeProvider(now)
    private val controller = mockk<BlobStorageController>()

    private var cachedKeys = emptySet<String>()
    private val cache = object : ImageCachePresence {
        override suspend fun holds(cacheKey: String): Boolean = cacheKey in cachedKeys
    }

    private val resolver = MediaUrlResolver(controller, cache, time)

    // Every avatar in these tests belongs to someone else, which is the case that needs a context.
    private val access = BlobAccessContext.Profile(listOf<Byte>(7))

    private val thumbId = BlobId(byteArrayOf(1))
    private val thumbKey = byteArrayOf(1).base58

    private fun media(expiresAt: Instant?, url: String = "https://cdn/stored") = MediaItem(
        listOf(
            MediaItemRendition(
                role = MediaItemRendition.Role.THUMBNAIL,
                blobId = thumbId,
                blob = BlobMetadata(
                    mimeType = "image/png",
                    sizeBytes = 1,
                    downloadUrl = url,
                    image = ImageMetadata(width = 160, height = 160, blurhash = "abc"),
                    expiresAtMillis = expiresAt?.toEpochMilliseconds(),
                ),
            )
        )
    )

    private fun freshMetadata(url: String) = BlobMetadata(
        mimeType = "image/png",
        sizeBytes = 1,
        downloadUrl = url,
        image = ImageMetadata(width = 160, height = 160, blurhash = "abc"),
        expiresAtMillis = (now + 10.minutes).toEpochMilliseconds(),
    )

    @Test
    fun `a live URL is handed straight back`() = runTest {
        val url = resolver.urlForSize(media(expiresAt = now + 10.minutes), 160, access)

        assertEquals("https://cdn/stored", url)
        coVerify(exactly = 0) { controller.refreshMetadata(any(), any()) }
    }

    @Test
    fun `an expired URL is re-minted`() = runTest {
        coEvery { controller.refreshMetadata(any(), any()) } returns
            Result.success(mapOf(thumbKey to freshMetadata("https://cdn/fresh")))

        val url = resolver.urlForSize(media(expiresAt = now - 1.minutes), 160, access)

        assertEquals("https://cdn/fresh", url)
        coVerify(exactly = 1) { controller.refreshMetadata(listOf(thumbId), any()) }
    }

    @Test
    fun `an expired URL whose bytes are already cached costs no round trip`() = runTest {
        cachedKeys = setOf(thumbKey)

        val url = resolver.urlForSize(media(expiresAt = now - 1.minutes), 160, access)

        assertEquals("https://cdn/stored", url)
        coVerify(exactly = 0) { controller.refreshMetadata(any(), any()) }
    }

    @Test
    fun `a failed re-mint falls back to the stored URL`() = runTest {
        coEvery { controller.refreshMetadata(any(), any()) } returns Result.failure(Throwable("offline"))

        val url = resolver.urlForSize(media(expiresAt = now - 1.minutes), 160, access)

        assertEquals("https://cdn/stored", url)
    }

    @Test
    fun `surfaces sharing a rendition share one re-mint`() = runTest {
        coEvery { controller.refreshMetadata(any(), any()) } returns
            Result.success(mapOf(thumbKey to freshMetadata("https://cdn/fresh")))
        val item = media(expiresAt = now - 1.minutes)

        val first = async { resolver.urlForSize(item, 160, access) }
        val second = async { resolver.urlForSize(item, 160, access) }

        assertEquals("https://cdn/fresh", first.await())
        assertEquals("https://cdn/fresh", second.await())
        coVerify(exactly = 1) { controller.refreshMetadata(any(), any()) }
    }

    @Test
    fun `refreshUrlForSize re-mints metadata that carries no expiry`() = runTest {
        // Rows persisted before expiry was modelled: nothing marks them stale but a failed load.
        coEvery { controller.refreshMetadata(any(), any()) } returns
            Result.success(mapOf(thumbKey to freshMetadata("https://cdn/fresh")))
        val item = media(expiresAt = null)

        assertEquals("https://cdn/stored", resolver.urlForSize(item, 160, access))
        assertEquals("https://cdn/fresh", resolver.refreshUrlForSize(item, 160, "https://cdn/stored", access))
    }

    @Test
    fun `reset drops URLs minted for the previous owner`() = runTest {
        coEvery { controller.refreshMetadata(any(), any()) } returns
            Result.success(mapOf(thumbKey to freshMetadata("https://cdn/fresh")))
        val item = media(expiresAt = now - 1.minutes)
        resolver.urlForSize(item, 160, access)

        resolver.reset()
        resolver.urlForSize(item, 160, access)

        coVerify(exactly = 2) { controller.refreshMetadata(any(), any()) }
    }

    @Test
    fun `a load that failed on an already-superseded URL reuses the mint it missed`() = runTest {
        // urlForSize re-mints while a surface is still showing the stale URL; that surface's
        // failure must not spend a second call re-minting what it can already be handed.
        coEvery { controller.refreshMetadata(any(), any()) } returns
            Result.success(mapOf(thumbKey to freshMetadata("https://cdn/fresh")))
        val item = media(expiresAt = now - 1.minutes)
        resolver.urlForSize(item, 160, access)

        val retry = resolver.refreshUrlForSize(item, 160, failedUrl = "https://cdn/stored", access)

        assertEquals("https://cdn/fresh", retry)
        coVerify(exactly = 1) { controller.refreshMetadata(any(), any()) }
    }

    @Test
    fun `the caller's access context is what re-mints someone else's media`() = runTest {
        // Without it the server omits blobs the caller doesn't own, so the re-mint comes back empty
        // and the avatar falls back — the failure this whole path exists to avoid.
        coEvery { controller.refreshMetadata(any(), any()) } returns
            Result.success(mapOf(thumbKey to freshMetadata("https://cdn/fresh")))

        resolver.urlForSize(media(expiresAt = now - 1.minutes), 160, access)

        coVerify(exactly = 1) { controller.refreshMetadata(listOf(thumbId), access) }
    }

    @Test
    fun `an item with no rendition resolves to null`() = runTest {
        assertEquals(null, resolver.urlForSize(MediaItem(emptyList()), 160, access))
        assertEquals(null, resolver.refreshUrlForSize(MediaItem(emptyList()), 160, null, access))
    }
}
