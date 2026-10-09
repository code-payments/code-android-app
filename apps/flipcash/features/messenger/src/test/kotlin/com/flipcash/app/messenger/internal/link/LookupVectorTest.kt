package com.flipcash.app.messenger.internal.link

import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.shared.chat.models.LinkCard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.fail

/**
 * Runs every `lookups` vector of `link_metadata.json`: the scripted responses by URL, the requests
 * made in order, the result, the card, and the keys the lookup and the resolver wrote. All failing
 * vectors are reported together. The fixture is a synced copy, so a disagreement is fixed in the
 * canonical file, never here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LookupVectorTest {

    private val realIo = object : DispatcherProvider {
        override val Default: CoroutineDispatcher = Dispatchers.Default
        override val Main: CoroutineDispatcher = Dispatchers.Default
        override val IO: CoroutineDispatcher = Dispatchers.IO
    }

    private fun fixture(): JSONObject = JSONObject(
        javaClass.classLoader!!.getResourceAsStream("link_metadata.json")!!
            .bufferedReader().use { it.readText() }
    )

    /** What the memory was asked to hold since the vector began, by key. Seeding is not recorded. */
    private class RecordingMemory : LinkCardMemory() {
        val puts = LinkedHashMap<String, LinkCard.Web.State>()
        var recording = false
        override fun putWeb(key: String, state: LinkCard.Web.State) {
            if (recording) puts[key] = state
            super.putWeb(key, state)
        }
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    private fun state(o: JSONObject?): LinkCard.Web.State.Resolved? = o?.let {
        LinkCard.Web.State.Resolved(
            it.getString("title"),
            it.optString("description").takeUnless { _ -> it.isNull("description") || !it.has("description") },
            it.optString("imageUrl").takeUnless { _ -> it.isNull("imageUrl") || !it.has("imageUrl") },
            it.getString("host"),
        )
    }

    private fun stored(row: JSONObject): LinkCard.Web.State = when (row.getString("state")) {
        "resolved" -> state(row.getJSONObject("card"))!!
        else -> LinkCard.Web.State.None
    }

    private fun htmlOf(r: JSONObject): ByteArray {
        val html = r.optString("html", "")
        if (!r.has("fillBytes")) return html.toByteArray()
        val marker = "<!--FILL-->"
        val pad = r.getInt("fillBytes") - (html.length - marker.length)
        check(pad >= 0) { "fillBytes smaller than the page" }
        return html.replace(marker, "x".repeat(pad)).toByteArray().also { check(it.size == r.getInt("fillBytes")) }
    }

    private class Counting(
        private val type: String?,
        private val bytes: ByteArray,
        private val pulled: AtomicLong,
    ) : ResponseBody() {
        override fun contentType() = type?.toMediaType()
        override fun contentLength() = -1L
        override fun source(): BufferedSource =
            object : ForwardingSource(Buffer().write(bytes)) {
                override fun read(sink: Buffer, byteCount: Long): Long =
                    super.read(sink, byteCount).also { if (it > 0) pulled.addAndGet(it) }
            }.buffer()
    }

    private fun runVector(v: JSONObject): List<String> {
        val name = v.getString("name")
        val problems = mutableListOf<String>()
        val responses = v.getJSONArray("responses").objects().associateBy { it.getString("url") }
        val seen = CopyOnWriteArrayList<String>()
        val pulled = AtomicLong()
        val client = webPreviewClient().newBuilder().addInterceptor(Interceptor { chain ->
            val req: Request = chain.request()
            val url = req.url.toString()
            seen += url
            val r = responses[url] ?: throw IOException("unscripted request $url")
            val builder = Response.Builder().request(req).protocol(Protocol.HTTP_1_1)
                .code(r.getInt("status")).message("m")
            if (r.has("location")) builder.header("Location", r.getString("location"))
            val type = if (r.has("contentType")) r.getString("contentType")
            else if (r.has("html")) "text/html; charset=utf-8" else null
            builder.body(Counting(type, htmlOf(r), pulled)).build()
        }).build()

        val memory = RecordingMemory()
        v.getJSONArray("cachedBefore").objects().forEach { memory.putWeb(it.getString("key"), stored(it)) }
        memory.recording = true

        val lookup = WebLinkLookup(client, { true }, realIo, memory = memory)
        var lookupResult: Result<LinkCard.Web.State>? = null
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val url = v.getString("url")
        val resolved = try {
            runBlocking {
                LinkCardResolver(
                    scope = scope,
                    giftCard = { Result.failure(IllegalStateException("unused")) },
                    tokenMetadata = { Result.failure(IllegalStateException("unused")) },
                    group = { Result.failure(IllegalStateException("unused")) },
                    user = { Result.failure(IllegalStateException("unused")) },
                    web = { lookup(it).also { r -> lookupResult = r } },
                    memory = memory,
                ).resolve(LinkCard.Web(url, 0, url.length)) as LinkCard.Web
            }
        } finally {
            scope.cancel()
        }

        val expectedFetches = v.getJSONArray("fetches").let { a -> (0 until a.length()).map { a.getString(it) } }
        if (seen != expectedFetches) problems += "fetches $seen, expected $expectedFetches"

        val result = lookupResult
        when (v.getString("result")) {
            "failure" -> if (result?.isFailure != true) problems += "expected a failure, got $result"
            "none" -> if (result?.getOrNull() != LinkCard.Web.State.None) problems += "expected None, got $result"
            "resolved" -> {
                val card = state(v.getJSONObject("card"))
                if (result?.getOrNull() != card) problems += "expected $card, got $result"
                if (resolved.state != card) problems += "resolver drew ${resolved.state}, expected $card"
            }
        }
        if (resolved.url != url) problems += "the card opens ${resolved.url}, expected the original $url"

        val expectedCached = v.getJSONArray("cached").objects().associate { it.getString("key") to stored(it) }
        if (memory.puts != expectedCached) problems += "cached ${memory.puts}, expected $expectedCached"

        if (name == "stops-after-head" && pulled.get() > 64 * 1024) {
            problems += "pulled ${pulled.get()} bytes, the body after the head must not be downloaded"
        }
        return problems.map { "$name: $it" }
    }

    @Test
    fun `every lookup vector in the fixture holds`() {
        val vectors = fixture().getJSONArray("lookups").objects()
        check(vectors.size == 20) { "expected 20 lookup vectors, found ${vectors.size}" }
        val failures = vectors.flatMap { v ->
            runCatching { runVector(v) }.getOrElse { listOf("${v.getString("name")}: threw $it") }
        }
        if (failures.isNotEmpty()) {
            fail("${failures.map { it.substringBefore(':') }.distinct().size} vector(s) failed:\n" + failures.joinToString("\n"))
        }
    }
}
