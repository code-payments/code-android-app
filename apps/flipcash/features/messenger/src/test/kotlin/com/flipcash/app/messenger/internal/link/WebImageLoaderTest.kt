package com.flipcash.app.messenger.internal.link

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * P22b and P22c: the preview loader keeps pictures in a store of its own, 20 MB, written only for
 * a successful answer and read whatever the headers said. The client here is a plain scripted one,
 * so these cases see Coil's own cache strategy and not the rules [WebImageInterceptor] adds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebImageLoaderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val url = "https://img.example.com/p.png"
    private val calls = AtomicInteger()

    private fun client(code: Int = 200, headers: Map<String, String> = emptyMap()) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            calls.incrementAndGet()
            Response.Builder()
                .request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("m")
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .body(byteArrayOf(1, 2, 3, 4).toResponseBody("image/png".toMediaType()))
                .build()
        }
        .build()

    private fun loader(client: OkHttpClient): ImageLoader = webPreviewImageLoader(context, client)

    private fun ImageLoader.load() {
        runBlocking { execute(ImageRequest.Builder(context).data(url).size(Size.ORIGINAL)
            .memoryCachePolicy(CachePolicy.DISABLED).build()) }
    }

    private fun ImageLoader.stored(): DiskCache.Snapshot? = diskCache?.openSnapshot(url)

    @Test
    fun `the loader's disk cache is its own, 20 MB, under the cache dir`() {
        val cache = assertNotNull(loader(client()).diskCache)

        assertEquals(20L * 1024 * 1024, cache.maxSize)
        assertEquals(File(context.cacheDir, "web_preview_images").absolutePath, cache.directory.toFile().absolutePath)
        assertNotEquals(File(context.cacheDir, "image_cache").absolutePath, cache.directory.toFile().absolutePath)
    }

    @Test
    fun `a successful answer is written under the image url`() {
        val loader = loader(client())
        loader.load()

        assertNotNull(loader.stored())
    }

    @Test
    fun `a failed answer is never written`() {
        for (code in listOf(404, 410, 301, 500)) {
            val loader = loader(client(code = code))
            loader.load()

            assertNull(loader.stored(), "HTTP $code should leave nothing on disk")
        }
    }

    @Test
    fun `no-store does not keep a picture off the disk`() {
        val loader = loader(client(headers = mapOf("Cache-Control" to "no-store")))
        loader.load()

        assertNotNull(loader.stored())
    }

    @Test
    fun `a stored picture is read again without a request whatever its headers said`() {
        val loader = loader(client(headers = mapOf("Cache-Control" to "max-age=0, must-revalidate")))
        loader.load()
        loader.load()

        assertEquals(1, calls.get())
    }

    @Test
    fun `removing a url from the store deletes its picture`() {
        val loader = loader(client())
        loader.load()
        assertNotNull(loader.stored())

        CoilWebImageStore(loader.diskCache).remove(url)

        assertNull(loader.stored())
    }
}
