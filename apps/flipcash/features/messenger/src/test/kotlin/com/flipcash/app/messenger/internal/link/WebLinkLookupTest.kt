package com.flipcash.app.messenger.internal.link

import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.shared.chat.models.LinkCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Fetch rules. Most cases script the answers through an application interceptor, so nothing touches
 * the network. The Content-Encoding case runs against a real TLS [MockWebServer], because only a real
 * call goes through OkHttp's bridge interceptor, which is what would strip the header.
 */
class WebLinkLookupTest {

    private val realIo = object : DispatcherProvider {
        override val Default: CoroutineDispatcher = Dispatchers.Default
        override val Main: CoroutineDispatcher = Dispatchers.Default
        override val IO: CoroutineDispatcher = Dispatchers.IO
    }

    private val html = """<html><head><title>Hello</title></head><body></body></html>"""

    private fun reply(
        request: Request,
        code: Int = 200,
        type: String? = "text/html; charset=utf-8",
        body: ByteArray = html.toByteArray(),
        headers: Map<String, String> = emptyMap(),
        repeated: List<Pair<String, String>> = emptyList(),
    ): Response = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("m")
        .apply { headers.forEach { (k, v) -> header(k, v) } }
        .apply { repeated.forEach { (k, v) -> addHeader(k, v) } }
        .body(body.toResponseBody(type?.toMediaType()))
        .build()

    private fun redirect(request: Request, to: String) = reply(request, 302, null, ByteArray(0), mapOf("Location" to to))

    private class Script(val handler: (Request) -> Response) {
        val seen = CopyOnWriteArrayList<Request>()
        val interceptor = Interceptor { chain -> chain.request().also { seen += it }.let(handler) }
    }

    private fun lookup(script: Script, enabled: Boolean = true) = WebLinkLookup(
        client = webPreviewClient().newBuilder().addInterceptor(script.interceptor).build(),
        enabled = { enabled },
        dispatchers = realIo,
    )

    private fun fetch(script: Script, url: String = "https://example.com/a", enabled: Boolean = true) =
        runBlocking { lookup(script, enabled)(url) }

    /** The lookup only requests port 443, so a test server is reached by moving the call after the checks. */
    private fun portTo(port: Int) = Interceptor { chain ->
        chain.proceed(chain.request().newBuilder().url(chain.request().url.newBuilder().port(port).build()).build())
    }

    private fun Result<LinkCard.Web.State>.state(): LinkCard.Web.State = getOrThrow()

    @Test
    fun `a 200 html page resolves with the host of the last url`() {
        val script = Script { req ->
            when (req.url.host) {
                "example.com" -> redirect(req, "https://www.final.org/page")
                else -> reply(req)
            }
        }
        val state = fetch(script).state()
        assertEquals(LinkCard.Web.State.Resolved("Hello", null, null, "final.org"), state)
    }

    @Test
    fun `three redirects are followed and a fourth gives none`() {
        val hops = AtomicInteger()
        val follows = Script { req ->
            if (hops.getAndIncrement() < 3) redirect(req, "https://example.com/${hops.get()}") else reply(req)
        }
        assertTrue(fetch(follows).state() is LinkCard.Web.State.Resolved)
        assertEquals(4, follows.seen.size)

        val never = Script { req -> redirect(req, "https://example.com/next") }
        assertEquals(LinkCard.Web.State.None, fetch(never).state())
        assertEquals(4, never.seen.size)
    }

    @Test
    fun `an ineligible redirect target gives none and is not requested`() {
        for (target in listOf(
            "http://example.com/",
            "https://10.0.0.1/",
            "https://printer.local/",
            "https://a.local./",
            "https://ex%61mple.com/",
        )) {
            val script = Script { req -> redirect(req, target) }
            assertEquals(LinkCard.Web.State.None, fetch(script).state(), target)
            assertEquals(1, script.seen.size, target)
        }
    }

    /** A hop to a port other than 443 gives none and is not requested (parity decision D12). */
    @Test
    fun `a redirect to another port gives none and is not requested`() {
        val script = Script { req -> redirect(req, "https://example.com:8443/") }
        assertEquals(LinkCard.Web.State.None, fetch(script).state())
        assertEquals(1, script.seen.size)
    }

    /** The escape check reads the Location's own authority, not the first `://` anywhere in it (parity decision D16). */
    @Test
    fun `a redirect with an escaped host or a backslash gives none`() {
        for (target in listOf(
            "//ex%61mple.org/x",
            "https://ex%61mple.org/x",
            "HTTPS://user@ex%61mple.org:443/x",
            "/a\\b",
            "https://example.com\\@evil.org/",
        )) {
            val script = Script { req -> redirect(req, target) }
            assertEquals(LinkCard.Web.State.None, fetch(script).state(), target)
            assertEquals(1, script.seen.size, target)
        }
    }

    @Test
    fun `a relative redirect keeps its host even when its query holds an escape`() {
        val script = Script { req ->
            if (req.url.encodedPath == "/redir") reply(req) else redirect(req, "/redir?to=https://a%20b")
        }
        assertTrue(fetch(script).state() is LinkCard.Web.State.Resolved)
        assertEquals(listOf("example.com", "example.com"), script.seen.map { it.url.host })
    }

    /** Every Content-Encoding value across repeated headers must be identity (parity decision D17). */
    @Test
    fun `any non identity content encoding across repeated headers gives none`() {
        for (values in listOf(listOf("gzip", "identity"), listOf("identity", "gzip"), listOf("identity, gzip"), listOf("br"))) {
            val script = Script { reply(it, repeated = values.map { v -> "Content-Encoding" to v }) }
            assertEquals(LinkCard.Web.State.None, fetch(script).state(), values.toString())
        }
        for (values in listOf(listOf("identity"), listOf("Identity", "identity, IDENTITY"))) {
            val script = Script { reply(it, repeated = values.map { v -> "Content-Encoding" to v }) }
            assertTrue(fetch(script).state() is LinkCard.Web.State.Resolved, values.toString())
        }
    }

    @Test
    fun `repeated locations that differ fail, equal ones are followed`() {
        val differ = Script { req ->
            reply(req, 302, null, ByteArray(0), repeated = listOf("Location" to "https://a.example/", "Location" to "https://b.example/"))
        }
        assertTrue(fetch(differ).isFailure)
        assertEquals(1, differ.seen.size)

        val same = Script { req ->
            if (req.url.host == "a.example") reply(req)
            else reply(req, 302, null, ByteArray(0), repeated = listOf("Location" to "https://a.example/", "Location" to "https://a.example/"))
        }
        assertTrue(fetch(same).state() is LinkCard.Web.State.Resolved)
        assertEquals(2, same.seen.size)
    }

    @Test
    fun `repeated content lengths that differ fail, equal ones do not`() {
        val differ = Script { reply(it, repeated = listOf("Content-Length" to "10", "Content-Length" to "11")) }
        assertTrue(fetch(differ).isFailure)

        val same = Script { reply(it, repeated = listOf("Content-Length" to "${html.length}", "Content-Length" to "${html.length}")) }
        assertTrue(fetch(same).state() is LinkCard.Web.State.Resolved)
    }

    /** One deadline covers every hop, and it starts once the permit is held (parity decision D18). */
    @Test
    fun `slow hops past the deadline fail and are not none`() {
        val script = Script { req ->
            Thread.sleep(150)
            if (req.url.encodedPath.length < 4) redirect(req, "https://example.com${req.url.encodedPath}x") else reply(req)
        }
        val slow = WebLinkLookup(webPreviewClient().newBuilder().addInterceptor(script.interceptor).build(), { true }, realIo, deadline = 300.milliseconds)
        val result = runBlocking { slow("https://example.com/") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() !is CancellationException)
        // The same hops inside a longer deadline resolve.
        val fast = WebLinkLookup(webPreviewClient().newBuilder().addInterceptor(script.interceptor).build(), { true }, realIo, deadline = 5.seconds)
        assertTrue(runBlocking { fast("https://example.com/") }.getOrThrow() is LinkCard.Web.State.Resolved)
    }

    @Test
    fun `time spent waiting for a permit does not count against the deadline`() {
        val script = Script { req -> Thread.sleep(250); reply(req) }
        val lookup = WebLinkLookup(webPreviewClient().newBuilder().addInterceptor(script.interceptor).build(), { true }, realIo, deadline = 400.milliseconds)
        val results = runBlocking(Dispatchers.IO) {
            (1..WebLinks.MAX_CONCURRENT + 1).map { async { lookup("https://example.com/$it") } }.awaitAll()
        }
        results.forEach { assertTrue(it.getOrThrow() is LinkCard.Web.State.Resolved) }
    }

    @Test
    fun `non html and 404 give none, 503 and io errors fail`() {
        assertEquals(LinkCard.Web.State.None, fetch(Script { reply(it, type = "application/json") }).state())
        assertEquals(LinkCard.Web.State.None, fetch(Script { reply(it, code = 404) }).state())
        assertTrue(fetch(Script { reply(it, code = 503) }).isFailure)
        assertTrue(fetch(Script { throw IOException("down") }).isFailure)
    }

    @Test
    fun `a body is read only up to the cap`() {
        val filler = " ".repeat(WebLinks.MAX_BODY_BYTES + 10)
        val late = "<html><head>$filler<title>Late</title></head></html>".toByteArray()
        assertEquals(LinkCard.Web.State.None, fetch(Script { reply(it, body = late) }).state())

        val early = "<html><head><title>Early</title>$filler</head></html>".toByteArray()
        assertEquals("Early", (fetch(Script { reply(it, body = early) }).state() as LinkCard.Web.State.Resolved).title)
    }

    @Test
    fun `requests carry the agreed headers and no credentials`() {
        val script = Script { reply(it) }
        fetch(script)
        val request = script.seen.single()
        assertNull(request.header("Cookie"))
        assertNull(request.header("Authorization"))
        assertEquals(WebLinks.USER_AGENT, request.header("User-Agent"))
        assertEquals("text/html,application/xhtml+xml", request.header("Accept"))
        assertEquals("identity", request.header("Accept-Encoding"))
    }

    @Test
    fun `with the flag off the result is a failure and nothing is requested`() {
        val script = Script { reply(it) }
        assertTrue(fetch(script, enabled = false).isFailure)
        assertTrue(script.seen.isEmpty())
    }

    @Test
    fun `six calls at once never have more than four requests open`() {
        val open = AtomicInteger()
        val peak = AtomicInteger()
        val release = CountDownLatch(1)
        val script = Script { req ->
            peak.accumulateAndGet(open.incrementAndGet()) { a, b -> maxOf(a, b) }
            try {
                release.await(10, TimeUnit.SECONDS)
                reply(req)
            } finally {
                open.decrementAndGet()
            }
        }
        val lookup = lookup(script)
        runBlocking(Dispatchers.IO) {
            val calls = (1..6).map { async { lookup("https://example.com/$it") } }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (open.get() < WebLinks.MAX_CONCURRENT && System.nanoTime() < deadline) Thread.sleep(10)
            // Give a fifth request the chance to (wrongly) start.
            Thread.sleep(200)
            assertEquals(WebLinks.MAX_CONCURRENT, open.get())
            release.countDown()
            calls.awaitAll().forEach { assertTrue(it.getOrThrow() is LinkCard.Web.State.Resolved) }
        }
        assertEquals(WebLinks.MAX_CONCURRENT, peak.get())
    }

    @Test
    fun `an encoded page gives none and OkHttp left the header visible`() {
        val host = "preview.example.com"
        val held = HeldCertificate.Builder().addSubjectAlternativeName(host).build()
        val serverCerts = HandshakeCertificates.Builder().heldCertificate(held).build()
        val clientCerts = HandshakeCertificates.Builder().addTrustedCertificate(held.certificate).build()
        val server = MockWebServer()
        server.useHttps(serverCerts.sslSocketFactory())
        server.enqueue(
            MockResponse.Builder().code(200)
                .addHeader("Content-Type", "text/html")
                .addHeader("Content-Encoding", "gzip")
                .body(html)
                .build()
        )
        server.start()
        try {
            var sawEncoding: String? = "unset"
            val client = webPreviewClient(Dns { listOf(InetAddress.getByName("127.0.0.1")) })
                .newBuilder()
                .sslSocketFactory(clientCerts.sslSocketFactory(), clientCerts.trustManager)
                .addInterceptor(portTo(server.port))
                .addInterceptor { chain ->
                    chain.proceed(chain.request()).also { sawEncoding = it.header("Content-Encoding") }
                }
                .build()
            val result = runBlocking {
                WebLinkLookup(client, { true }, realIo)("https://$host/")
            }
            assertEquals("gzip", sawEncoding, "OkHttp must not strip Content-Encoding")
            assertEquals("identity", server.takeRequest().headers["Accept-Encoding"])
            assertEquals(LinkCard.Web.State.None, result.getOrThrow())
        } finally {
            server.close()
        }
    }

    @Test
    fun `the default client pins its transport settings`() {
        val client = webPreviewClient()
        assertEquals(false, client.followRedirects)
        assertEquals(false, client.followSslRedirects)
        assertTrue(client.dns is PublicOnlyDns)
        assertEquals(CookieJar.NO_COOKIES, client.cookieJar)
        assertNull(client.cache)
        assertEquals(Proxy.NO_PROXY, client.proxy)
        assertEquals(5_000, client.connectTimeoutMillis)
        assertEquals(5_000, client.readTimeoutMillis)
        assertEquals(10_000, client.callTimeoutMillis)
    }

    /** A system proxy would take the CONNECT and resolve the name itself, so [PublicOnlyDns] would never run. */
    @Test
    fun `a system proxy is never asked and the dns still runs`() {
        val proxy = MockWebServer()
        proxy.start()
        try {
            val asked = CopyOnWriteArrayList<String>()
            val resolved = CopyOnWriteArrayList<String>()
            val selector = object : ProxySelector() {
                override fun select(uri: URI?): List<Proxy> {
                    asked += uri.toString()
                    return listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxy.port)))
                }

                override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
            }
            val client = webPreviewClient(Dns { host -> resolved += host; throw java.net.UnknownHostException(host) })
                .newBuilder().proxySelector(selector).build()
            val result = runBlocking { WebLinkLookup(client, { true }, realIo)("https://example.com/") }
            assertTrue(result.isFailure)
            assertEquals(0, proxy.requestCount)
            assertTrue(asked.isEmpty(), "proxy selector was consulted: $asked")
            assertEquals(listOf("example.com"), resolved.toList())
        } finally {
            proxy.close()
        }
    }

    /** The lookup's loop follows redirects and checks each hop; the client must not do it first. */
    @Test
    fun `the client does not follow a redirect by itself`() {
        val host = "preview.example.com"
        val held = HeldCertificate.Builder().addSubjectAlternativeName(host).build()
        val serverCerts = HandshakeCertificates.Builder().heldCertificate(held).build()
        val clientCerts = HandshakeCertificates.Builder().addTrustedCertificate(held.certificate).build()
        val server = MockWebServer()
        server.useHttps(serverCerts.sslSocketFactory())
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "http://$host/next").build())
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "text/html").body(html).build())
        server.start()
        try {
            val client = webPreviewClient(Dns { listOf(InetAddress.getByName("127.0.0.1")) })
                .newBuilder()
                .sslSocketFactory(clientCerts.sslSocketFactory(), clientCerts.trustManager)
                .addInterceptor(portTo(server.port))
                .build()
            val result = runBlocking { WebLinkLookup(client, { true }, realIo)("https://$host/") }
            assertEquals(LinkCard.Web.State.None, result.getOrThrow())
            assertEquals(1, server.requestCount)
        } finally {
            server.close()
        }
    }
}
