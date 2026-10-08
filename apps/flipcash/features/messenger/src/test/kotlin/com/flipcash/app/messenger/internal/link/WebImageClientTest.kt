package com.flipcash.app.messenger.internal.link

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.BufferedSource
import okio.Buffer
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The image client follows the page rules. Most cases script the answers through an application
 * interceptor placed after the client's own, so it sees exactly what the client sends and nothing
 * touches the network. The cases about the transport itself run against a real TLS [MockWebServer].
 */
class WebImageClientTest {

    private val png = byteArrayOf(1, 2, 3, 4)

    private fun reply(
        request: Request,
        code: Int = 200,
        type: String? = "image/png",
        body: ByteArray = png,
        headers: Map<String, String> = emptyMap(),
        repeated: List<Pair<String, String>> = emptyList(),
        responseBody: ResponseBody? = null,
    ): Response = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("m")
        .apply { headers.forEach { (k, v) -> header(k, v) } }
        .apply { repeated.forEach { (k, v) -> addHeader(k, v) } }
        .body(responseBody ?: body.toResponseBody(type?.toMediaType()))
        .build()

    private fun redirect(request: Request, to: String) = reply(request, 302, null, ByteArray(0), mapOf("Location" to to))

    private class Script(val handler: (Request) -> Response) {
        val seen = CopyOnWriteArrayList<Request>()
        val interceptor = Interceptor { chain -> chain.request().also { seen += it }.let(handler) }
    }

    private fun client(script: Script, enabled: Boolean = true): OkHttpClient =
        webImageClient(enabled = { enabled }).newBuilder().addInterceptor(script.interceptor).build()

    /** The bytes the caller would decode, or the failure it would see. */
    private fun load(
        client: OkHttpClient,
        url: String = "https://example.com/a.png",
        configure: Request.Builder.() -> Unit = {},
    ): Result<ByteArray> = runCatching {
        client.newCall(Request.Builder().url(url).apply(configure).build()).execute().use { it.body.bytes() }
    }

    private fun load(script: Script, url: String = "https://example.com/a.png", enabled: Boolean = true) =
        load(client(script, enabled), url)

    /** The client only requests port 443, so a test server is reached by moving the call after the checks. */
    private fun portTo(port: Int) = Interceptor { chain ->
        chain.proceed(chain.request().newBuilder().url(chain.request().url.newBuilder().port(port).build()).build())
    }

    private class Tls(host: String) {
        val held = HeldCertificate.Builder().addSubjectAlternativeName(host).build()
        val server = MockWebServer().apply {
            useHttps(HandshakeCertificates.Builder().heldCertificate(held).build().sslSocketFactory())
        }
        val clientCerts = HandshakeCertificates.Builder().addTrustedCertificate(held.certificate).build()
    }

    private fun tlsClient(tls: Tls, dns: Dns = Dns { listOf(InetAddress.getByName("127.0.0.1")) }) =
        webImageClient(dns = dns)
            .newBuilder()
            .sslSocketFactory(tls.clientCerts.sslSocketFactory(), tls.clientCerts.trustManager)
            .addInterceptor(portTo(tls.server.port))
            .build()

    @Test
    fun `a 200 image comes back whole`() {
        assertContentEquals(png, load(Script { reply(it) }).getOrThrow())
    }

    @Test
    fun `requests carry the agreed headers and no credentials whatever the caller set`() {
        val script = Script { reply(it) }
        load(client(script)) {
            header("Accept", "*/*")
            header("Accept-Encoding", "gzip")
            header("User-Agent", "other")
            header("Cookie", "a=b")
            header("Authorization", "Bearer x")
        }
        val request = script.seen.single()
        assertEquals(WebLinks.USER_AGENT, request.header("User-Agent"))
        assertEquals("image/*", request.header("Accept"))
        assertEquals("identity", request.header("Accept-Encoding"))
        assertNull(request.header("Cookie"))
        assertNull(request.header("Authorization"))
    }

    @Test
    fun `the headers reach the wire`() {
        val host = "img.example.com"
        val tls = Tls(host)
        tls.server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "image/png").body(Buffer().write(png)).build())
        tls.server.start()
        try {
            assertContentEquals(png, load(tlsClient(tls), "https://$host/a.png").getOrThrow())
            val headers = tls.server.takeRequest(5, TimeUnit.SECONDS)!!.headers
            assertEquals("identity", headers["Accept-Encoding"])
            assertEquals("image/*", headers["Accept"])
            assertEquals(WebLinks.USER_AGENT, headers["User-Agent"])
            assertNull(headers["Cookie"])
        } finally {
            tls.server.close()
        }
    }

    @Test
    fun `three redirects are followed and a fourth fails, four requests at most`() {
        val hops = AtomicInteger()
        val follows = Script { req ->
            if (hops.getAndIncrement() < 3) redirect(req, "https://example.com/${hops.get()}.png") else reply(req)
        }
        assertContentEquals(png, load(follows).getOrThrow())
        assertEquals(4, follows.seen.size)

        val never = Script { req -> redirect(req, "https://example.com/next.png") }
        assertTrue(load(never).isFailure)
        assertEquals(4, never.seen.size)
    }

    @Test
    fun `an unusable redirect target fails and is not requested`() {
        for (target in listOf(
            "http://example.com/a.png",
            "https://10.0.0.1/a.png",
            "https://printer.local/a.png",
            "https://a.local./a.png",
            "https://ex%61mple.com/a.png",
            "https://example.com\\@evil.com/a.png",
            "https://example.com:8443/a.png",
            "https://example.com:444/a.png",
        )) {
            val script = Script { req -> redirect(req, target) }
            assertTrue(load(script).isFailure, target)
            assertEquals(1, script.seen.size, target)
        }
    }

    @Test
    fun `a redirect without a location fails`() {
        val script = Script { req -> reply(req, 302, null, ByteArray(0)) }
        assertTrue(load(script).isFailure)
        assertEquals(1, script.seen.size)
    }

    @Test
    fun `an unusable first url is never requested`() {
        for (url in listOf("http://example.com/a.png", "https://example.com:8443/a.png", "https://10.0.0.1/a.png", "https://localhost/a.png", "https://intranet/a.png")) {
            val script = Script { reply(it) }
            assertTrue(load(script, url).isFailure, url)
            assertTrue(script.seen.isEmpty(), url)
        }
    }

    @Test
    fun `repeated locations that differ fail, equal ones are followed`() {
        val differ = Script { req ->
            reply(req, 302, null, ByteArray(0), repeated = listOf("Location" to "https://a.example.com/", "Location" to "https://b.example.com/"))
        }
        assertTrue(load(differ).isFailure)
        assertEquals(1, differ.seen.size)

        val same = Script { req ->
            if (req.url.host == "example.com") {
                reply(req, 302, null, ByteArray(0), repeated = listOf("Location" to "https://a.example.com/x.png", "Location" to "https://a.example.com/x.png"))
            } else reply(req)
        }
        assertContentEquals(png, load(same).getOrThrow())
    }

    @Test
    fun `any non identity content encoding across repeated headers fails`() {
        for (values in listOf(listOf("gzip"), listOf("identity, gzip"), listOf("identity", "gzip"), listOf(""), listOf("identity,"), listOf("br"))) {
            val script = Script { reply(it, repeated = values.map { v -> "Content-Encoding" to v }) }
            assertTrue(load(script).isFailure, values.toString())
        }
        val identity = Script { reply(it, repeated = listOf("Content-Encoding" to "identity", "Content-Encoding" to "Identity")) }
        assertContentEquals(png, load(identity).getOrThrow())
    }

    @Test
    fun `a gzip reply from a real server fails`() {
        val host = "img.example.com"
        val tls = Tls(host)
        tls.server.enqueue(
            MockResponse.Builder().code(200).addHeader("Content-Type", "image/png").addHeader("Content-Encoding", "gzip")
                .body(Buffer().write(png)).build()
        )
        tls.server.start()
        try {
            assertTrue(load(tlsClient(tls), "https://$host/a.png").isFailure)
            assertEquals("identity", tls.server.takeRequest(5, TimeUnit.SECONDS)!!.headers["Accept-Encoding"])
        } finally {
            tls.server.close()
        }
    }

    @Test
    fun `repeated content lengths that differ fail`() {
        val script = Script { reply(it, repeated = listOf("Content-Length" to "4", "Content-Length" to "5")) }
        assertTrue(load(script).isFailure)
    }

    @Test
    fun `a declared length over the cap fails before the body is read`() {
        val big = ByteArray(WebLinks.MAX_IMAGE_BYTES + 1)
        assertTrue(load(Script { reply(it, body = big) }).isFailure)
    }

    @Test
    fun `an undeclared length is cut off at the cap and the load fails`() {
        fun unknownLength(size: Int) = object : ResponseBody() {
            override fun contentType() = "image/png".toMediaType()
            override fun contentLength() = -1L
            override fun source(): BufferedSource = Buffer().write(ByteArray(size))
        }
        val over = load(Script { reply(it, responseBody = unknownLength(WebLinks.MAX_IMAGE_BYTES + 1)) })
        assertTrue(over.isFailure)
        val exact = load(Script { reply(it, responseBody = unknownLength(WebLinks.MAX_IMAGE_BYTES)) })
        assertEquals(WebLinks.MAX_IMAGE_BYTES, exact.getOrThrow().size)
    }

    @Test
    fun `a body exactly at the cap is accepted`() {
        val body = ByteArray(WebLinks.MAX_IMAGE_BYTES)
        assertEquals(WebLinks.MAX_IMAGE_BYTES, load(Script { reply(it, body = body) }).getOrThrow().size)
    }

    @Test
    fun `a type that is not an image fails`() {
        for (type in listOf("text/html", "application/json", "application/octet-stream", null)) {
            assertTrue(load(Script { reply(it, type = type) }).isFailure, type.toString())
        }
        assertContentEquals(png, load(Script { reply(it, type = "image/webp") }).getOrThrow())
    }

    @Test
    fun `404 and 503 fail`() {
        assertTrue(load(Script { reply(it, code = 404) }).isFailure)
        assertTrue(load(Script { reply(it, code = 503) }).isFailure)
        assertTrue(load(Script { throw IOException("down") }).isFailure)
    }

    @Test
    fun `with the flag off nothing is requested`() {
        val script = Script { reply(it) }
        assertTrue(load(script, enabled = false).isFailure)
        assertTrue(script.seen.isEmpty())
    }

    @Test
    fun `the client pins its transport settings`() {
        val client = webImageClient()
        assertEquals(false, client.followRedirects)
        assertEquals(false, client.followSslRedirects)
        assertTrue(client.dns is PublicOnlyDns)
        assertEquals(okhttp3.CookieJar.NO_COOKIES, client.cookieJar)
        assertNull(client.cache)
        assertEquals(Proxy.NO_PROXY, client.proxy)
    }

    /** A system proxy would take the CONNECT and resolve the name itself, so the public-only Dns would never run. */
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
            val client = webImageClient(dns = Dns { host -> resolved += host; throw java.net.UnknownHostException(host) })
                .newBuilder().proxySelector(selector).build()
            assertTrue(load(client).isFailure)
            assertEquals(0, proxy.requestCount)
            assertTrue(asked.isEmpty(), "proxy selector was consulted: $asked")
            assertEquals(listOf("example.com"), resolved.toList())
        } finally {
            proxy.close()
        }
    }

    /**
     * The loop follows redirects and checks each hop; the client must not do it first. The target is
     * https on an eligible host but on the test server's own port, which the loop refuses (D12) and
     * OkHttp's follow-up would reach.
     */
    @Test
    fun `the transport does not follow a redirect by itself`() {
        val host = "img.example.com"
        val tls = Tls(host)
        tls.server.start()
        tls.server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "https://$host:${tls.server.port}/next.png").build())
        tls.server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "image/png").body(Buffer().write(png)).build())
        try {
            assertTrue(load(tlsClient(tls), "https://$host/a.png").isFailure)
            assertEquals(1, tls.server.requestCount)
        } finally {
            tls.server.close()
        }
    }

    @Test
    fun `a name that resolves only to private addresses fails without a request`() {
        val tls = Tls("img.example.com")
        tls.server.start()
        try {
            val client = tlsClient(tls, dns = PublicOnlyDns { listOf(InetAddress.getByName("127.0.0.1")) })
            assertTrue(load(client, "https://img.example.com/a.png").isFailure)
            assertEquals(0, tls.server.requestCount)
        } finally {
            tls.server.close()
        }
    }
}
