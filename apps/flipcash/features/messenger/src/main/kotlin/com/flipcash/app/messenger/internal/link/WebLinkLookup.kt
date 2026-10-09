package com.flipcash.app.messenger.internal.link

import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.shared.chat.models.LinkCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.BufferedSource
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/** Drops private, loopback and link-local answers, so a public name cannot point the fetch inward. */
internal class PublicOnlyDns(private val delegate: Dns = Dns.SYSTEM) : Dns {
    override fun lookup(hostname: String): List<InetAddress> =
        delegate.lookup(hostname).filterNot { it.isPrivate() }
            .ifEmpty { throw UnknownHostException("$hostname resolves only to private addresses") }
}

/**
 * The fixture's `addresses` rule. Java already turns an IPv4-mapped IPv6 literal into an [Inet4Address].
 * A NAT64 address (`64:ff9b::/96`) is judged by the IPv4 address in its last 32 bits, a mapped one
 * the same way. 6to4 (`2002::/16`) and IPv4-compatible (`::/96`) addresses are always private, as
 * are the IPv4 special-use blocks 192.0.0.0/24, 192.0.2.0/24, 198.18.0.0/15 and 240.0.0.0/4
 * (parity decision D14).
 */
internal fun InetAddress.isPrivate(): Boolean {
    nat64Embedded()?.let { return it.isPrivate() }
    if (isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isAnyLocalAddress || isMulticastAddress) return true
    val b = address.map { it.toInt() and 0xFF }
    return when (this) {
        is Inet4Address -> b[0] == 0 || (b[0] == 100 && (b[1] and 0xC0) == 64) || b[0] >= 240 ||
            (b[0] == 192 && b[1] == 0 && (b[2] == 0 || b[2] == 2)) || (b[0] == 198 && (b[1] and 0xFE) == 18)
        is Inet6Address -> (b[0] and 0xFE) == 0xFC || (b[0] == 0x20 && b[1] == 0x02) || (0 until 12).all { b[it] == 0 }
        else -> true
    }
}

private fun InetAddress.nat64Embedded(): InetAddress? {
    if (this !is Inet6Address) return null
    val b = address
    val nat64 = intArrayOf(0x00, 0x64, 0xFF, 0x9B).let { p ->
        p.indices.all { (b[it].toInt() and 0xFF) == p[it] } && (4 until 12).all { b[it].toInt() == 0 }
    }
    val mapped = (0 until 10).all { b[it].toInt() == 0 } && b[10].toInt() == -1 && b[11].toInt() == -1
    return if (nat64 || mapped) InetAddress.getByAddress(b.copyOfRange(12, 16)) else null
}

/**
 * No cookie jar, no authenticator and no cache, and it must not be built from an app client whose
 * interceptors add credentials. Redirects are followed by hand so each hop is checked. It never
 * uses a system proxy, which would resolve the name itself and so bypass [PublicOnlyDns].
 */
internal fun webPreviewClient(dns: Dns = PublicOnlyDns()): OkHttpClient = webClientBuilder(dns).build()

/** The transport settings every outside fetch, page or image, shares. */
internal fun webClientBuilder(dns: Dns): OkHttpClient.Builder = OkHttpClient.Builder()
    .dns(dns)
    .proxy(Proxy.NO_PROXY)
    .cookieJar(CookieJar.NO_COOKIES)
    .cache(null)
    .followRedirects(false)
    .followSslRedirects(false)
    .connectTimeout(WebLinks.TIMEOUT.toJavaDuration())
    .readTimeout(WebLinks.TIMEOUT.toJavaDuration())
    .callTimeout((WebLinks.TIMEOUT * 2).toJavaDuration())

/** Port 443 only, on every hop (parity decision D12), https, and a host a preview may fetch (D13). */
internal fun HttpUrl.isFetchable(): Boolean = scheme == "https" && port == 443 && WebLinks.isEligibleHost(host)

/** Repeated headers that disagree are a smuggling shape; fail rather than pick one (parity decision D17). */
internal fun Response.requireConsistentLength() {
    if (headers.values("Content-Length").distinct().size > 1) throw IOException("conflicting Content-Length")
}

/**
 * Where a redirect points, or null when it is unusable: no `Location`, a backslash or escaped host
 * in it (D11, D16), or one that does not resolve. Throws when repeated `Location` headers disagree.
 */
internal fun Response.redirectTarget(current: HttpUrl): HttpUrl? {
    val locations = headers.values("Location").distinct()
    if (locations.size > 1) throw IOException("conflicting Location")
    val location = locations.firstOrNull()
    if (location == null || WebLinks.isUnsafeLocation(location)) return null
    return current.resolve(location)
}

/** Every value of every repeated Content-Encoding header, each comma-separated token, must be identity (D7, D17). */
internal fun Response.isEncoded() = headers.values("Content-Encoding")
    .flatMap { it.split(',') }
    .any { !it.trim().equals("identity", ignoreCase = true) }

/** The client implementation of the spec's LinkMetadataSource. A server RPC replaces it. */
internal class WebLinkLookup(
    private val client: OkHttpClient,
    private val enabled: suspend () -> Boolean,
    private val dispatchers: DispatcherProvider,
    private val deadline: Duration = WebLinks.LOOKUP_DEADLINE,
) {
    private val inFlight = Semaphore(WebLinks.MAX_CONCURRENT)

    /**
     * One [deadline] covers every hop. It starts once the permit is held, so waiting behind other
     * lookups does not spend it. Running out is a failure, not a None: the page may be fine, and a
     * failure is not cached (parity decision D18).
     */
    suspend operator fun invoke(url: String): Result<LinkCard.Web.State> = inFlight.withPermit {
        try {
            withTimeout(deadline) { fetch(url) }
        } catch (e: TimeoutCancellationException) {
            Result.failure(IOException("lookup deadline", e))
        }
    }

    private suspend fun fetch(url: String): Result<LinkCard.Web.State> = withContext(dispatchers.IO) {
        if (!enabled()) return@withContext Result.failure(IllegalStateException("web previews off"))
        runCatching {
            var current = url.toHttpUrl()
            // The first request plus MAX_REDIRECTS redirects. A redirect answering the last one is None.
            repeat(WebLinks.MAX_REDIRECTS + 1) {
                // Port 443 only, on every hop (parity decision D12).
                if (!current.isFetchable()) return@runCatching LinkCard.Web.State.None
                client.newCall(request(current)).await().use { response ->
                    response.requireConsistentLength()
                    when {
                        response.isRedirect -> {
                            current = response.redirectTarget(current) ?: return@runCatching LinkCard.Web.State.None
                        }
                        response.code >= 500 -> throw IOException("HTTP ${response.code}")
                        !response.isSuccessful -> return@runCatching LinkCard.Web.State.None
                        !response.isHtml() -> return@runCatching LinkCard.Web.State.None
                        // The caps count bytes read, so a compressed body is not read at all.
                        response.isEncoded() -> return@runCatching LinkCard.Web.State.None
                        else -> return@runCatching WebPageParser.parse(response.capped(), current.toString())
                            ?: LinkCard.Web.State.None
                    }
                }
            }
            LinkCard.Web.State.None
        }.onFailure { if (it is CancellationException) throw it }
    }

    // Set on the request, so OkHttp's bridge sees Accept-Encoding and leaves the body encoded.
    private fun request(url: HttpUrl) = Request.Builder().url(url)
        .header("User-Agent", WebLinks.USER_AGENT)
        .header("Accept", "text/html,application/xhtml+xml")
        .header("Accept-Encoding", "identity")
        .build()

    private fun Response.isHtml() = body.contentType()?.let {
        it.subtype == "html" || it.subtype == "xhtml+xml"
    } ?: false

    private fun Response.capped(): ByteArray = body.source().readHead(WebLinks.MAX_BODY_BYTES)
}

/**
 * Reads up to [cap] bytes of a page, stopping early once the head has ended: the first `</head` or
 * `<body`, either case, as [WebPageParser] finds it. A marker split across two reads is still seen,
 * because each scan restarts a marker's length before the new bytes. What follows is never pulled
 * from the connection. Reads [chunk] bytes at a time.
 */
internal fun BufferedSource.readHead(cap: Int, chunk: Long = 8192): ByteArray {
    val out = Buffer()
    while (out.size < cap) {
        val scanFrom = maxOf(0L, out.size - (HEAD_END_LONGEST - 1))
        if (read(out, minOf(chunk, cap - out.size)) < 0) break
        if (out.hasHeadEnd(scanFrom)) break
    }
    return out.readByteArray()
}

private const val HEAD_END_LONGEST = 6L

private fun Buffer.hasHeadEnd(from: Long): Boolean {
    var i = from
    while (i < size) {
        if (this[i] == '<'.code.toByte()) {
            if (matchesAt(i, "</head") || matchesAt(i, "<body")) return true
        }
        i++
    }
    return false
}

private fun Buffer.matchesAt(at: Long, marker: String): Boolean {
    if (at + marker.length > size) return false
    for (k in marker.indices) {
        val b = this[at + k].toInt().toChar().lowercaseChar()
        if (b != marker[k]) return false
    }
    return true
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response) { _, value, _ -> value.close() }
        }
    })
}
