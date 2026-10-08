package com.flipcash.app.messenger.internal.link

import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import java.io.IOException

/**
 * The client for a preview's picture, built under the same rules as [webPreviewClient]: no cookies,
 * no cache, no system proxy, [PublicOnlyDns], and no redirect followed by OkHttp itself.
 * [WebImageInterceptor] follows them by hand and holds the image to the page rules (decision 4).
 *
 * [enabled] is the kill switch: off, the call fails without a request.
 */
internal fun webImageClient(dns: Dns = PublicOnlyDns(), enabled: () -> Boolean = { true }): OkHttpClient =
    webClientBuilder(dns)
        .addInterceptor(WebImageInterceptor(enabled))
        .build()

/**
 * An application interceptor, so it decides every hop itself: OkHttp's follow-up sits below it and
 * must stay off, or `proceed` would hand back the last hop with the checks skipped.
 *
 * The request is rebuilt for each hop, so what the caller set (cookies, credentials, a different
 * `Accept-Encoding`) is not sent. A failure is an [IOException], which the loader treats as "no
 * image"; the card then draws without one.
 */
internal class WebImageInterceptor(private val enabled: () -> Boolean) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!enabled()) throw IOException("web previews off")
        var current = chain.request().url
        // The first request plus MAX_REDIRECTS redirects, as for pages.
        repeat(WebLinks.MAX_REDIRECTS + 1) {
            if (!current.isFetchable()) throw IOException("image url not fetchable")
            val response = chain.proceed(request(current))
            if (!response.isRedirect) return response.checkedImage()
            current = response.use {
                it.requireConsistentLength()
                it.redirectTarget(current)
            } ?: throw IOException("unusable image redirect")
        }
        throw IOException("too many image redirects")
    }

    // Set on the request, so OkHttp's bridge sees Accept-Encoding and leaves the body encoded.
    private fun request(url: HttpUrl) = Request.Builder().url(url)
        .header("User-Agent", WebLinks.USER_AGENT)
        .header("Accept", "image/*")
        .header("Accept-Encoding", "identity")
        .build()

    private fun Response.checkedImage(): Response {
        try {
            requireConsistentLength()
            if (!isSuccessful) throw IOException("HTTP $code")
            if (body.contentType()?.type != "image") throw IOException("not an image")
            // The cap counts bytes read, so a compressed body is not read at all.
            if (isEncoded()) throw IOException("encoded image")
            return newBuilder().body(body.capped(WebLinks.MAX_IMAGE_BYTES.toLong())).build()
        } catch (e: IOException) {
            close()
            throw e
        }
    }

    /**
     * A declared length over [limit] fails at once. Otherwise reading past it throws, so a truncated
     * picture is never handed on to be decoded.
     */
    private fun ResponseBody.capped(limit: Long): ResponseBody {
        if (contentLength() > limit) throw IOException("image over cap")
        val counting = object : ForwardingSource(source()) {
            private var total = 0L
            override fun read(sink: Buffer, byteCount: Long): Long {
                val read = super.read(sink, byteCount)
                if (read > 0) {
                    total += read
                    if (total > limit) throw IOException("image over cap")
                }
                return read
            }
        }
        return counting.buffer().asResponseBody(contentType(), contentLength())
    }
}
