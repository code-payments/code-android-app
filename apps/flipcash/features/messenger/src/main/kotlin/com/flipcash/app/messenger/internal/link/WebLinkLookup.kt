package com.flipcash.app.messenger.internal.link

import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.shared.chat.models.LinkCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.toJavaDuration

/** Drops private, loopback and link-local answers, so a public name cannot point the fetch inward. */
internal class PublicOnlyDns(private val delegate: Dns = Dns.SYSTEM) : Dns {
    override fun lookup(hostname: String): List<InetAddress> =
        delegate.lookup(hostname).filterNot { it.isPrivate() }
            .ifEmpty { throw UnknownHostException("$hostname resolves only to private addresses") }
}

/**
 * The fixture's `addresses` rule. Java already turns an IPv4-mapped IPv6 literal into an [Inet4Address].
 * A NAT64 address (`64:ff9b::/96`) is judged by the IPv4 address in its last 32 bits (parity decision D14).
 */
internal fun InetAddress.isPrivate(): Boolean {
    nat64Embedded()?.let { return it.isPrivate() }
    if (isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isAnyLocalAddress || isMulticastAddress) return true
    val b = address.map { it.toInt() and 0xFF }
    return when (this) {
        is Inet4Address -> b[0] == 0 || (b[0] == 100 && (b[1] and 0xC0) == 64) || b.all { it == 255 }
        is Inet6Address -> (b[0] and 0xFE) == 0xFC
        else -> true
    }
}

private fun InetAddress.nat64Embedded(): InetAddress? {
    if (this !is Inet6Address) return null
    val b = address
    val prefix = intArrayOf(0x00, 0x64, 0xFF, 0x9B)
    val inPrefix = prefix.indices.all { (b[it].toInt() and 0xFF) == prefix[it] } && (4 until 12).all { b[it].toInt() == 0 }
    return if (inPrefix) InetAddress.getByAddress(b.copyOfRange(12, 16)) else null
}

/**
 * No cookie jar, no authenticator and no cache, and it must not be built from an app client whose
 * interceptors add credentials. Redirects are followed by hand so each hop is checked. It never
 * uses a system proxy, which would resolve the name itself and so bypass [PublicOnlyDns].
 */
internal fun webPreviewClient(dns: Dns = PublicOnlyDns()): OkHttpClient = OkHttpClient.Builder()
    .dns(dns)
    .proxy(Proxy.NO_PROXY)
    .cookieJar(CookieJar.NO_COOKIES)
    .cache(null)
    .followRedirects(false)
    .followSslRedirects(false)
    .connectTimeout(WebLinks.TIMEOUT.toJavaDuration())
    .readTimeout(WebLinks.TIMEOUT.toJavaDuration())
    .callTimeout((WebLinks.TIMEOUT * 2).toJavaDuration())
    .build()

/** The client implementation of the spec's LinkMetadataSource. A server RPC replaces it. */
internal class WebLinkLookup(
    private val client: OkHttpClient,
    private val enabled: suspend () -> Boolean,
    private val dispatchers: DispatcherProvider,
) {
    private val inFlight = Semaphore(WebLinks.MAX_CONCURRENT)

    suspend operator fun invoke(url: String): Result<LinkCard.Web.State> = inFlight.withPermit { fetch(url) }

    private suspend fun fetch(url: String): Result<LinkCard.Web.State> = withContext(dispatchers.IO) {
        if (!enabled()) return@withContext Result.failure(IllegalStateException("web previews off"))
        runCatching {
            var current = url.toHttpUrl()
            // The first request plus MAX_REDIRECTS redirects. A redirect answering the last one is None.
            repeat(WebLinks.MAX_REDIRECTS + 1) {
                // Port 443 only, on every hop (parity decision D12).
                if (current.scheme != "https" || current.port != 443 || !WebLinks.isEligibleHost(current.host)) {
                    return@runCatching LinkCard.Web.State.None
                }
                client.newCall(request(current)).await().use { response ->
                    when {
                        response.isRedirect -> {
                            val location = response.header("Location")
                            if (location == null || WebLinks.hasEscapedHost(location)) {
                                return@runCatching LinkCard.Web.State.None
                            }
                            current = current.resolve(location) ?: return@runCatching LinkCard.Web.State.None
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

    private fun Response.isEncoded() = header("Content-Encoding")
        ?.let { !it.trim().equals("identity", ignoreCase = true) } ?: false

    private fun Response.capped(): ByteArray {
        val source = body.source()
        source.request(WebLinks.MAX_BODY_BYTES.toLong())
        return source.buffer.readByteArray(minOf(source.buffer.size, WebLinks.MAX_BODY_BYTES.toLong()))
    }
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
