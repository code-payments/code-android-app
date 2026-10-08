package com.flipcash.app.messenger.internal.link

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** Rules shared by every step of a web link preview. Mirrors `link_metadata.json`. */
internal object WebLinks {
    const val MAX_BODY_BYTES = 512 * 1024
    const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
    const val MAX_REDIRECTS = 3
    const val MAX_CONCURRENT = 4
    val TIMEOUT = 5.seconds
    val RESOLVED_TTL = 168.hours
    val EMPTY_TTL = 24.hours
    const val USER_AGENT = "Mozilla/5.0 (compatible; FlipcashLinkPreview/1.0)"

    private val blockedSuffixes = listOf(".localhost", ".local", ".internal")
    private val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    private val numericLabel = Regex("""^(0x[0-9a-f]*|\d+)$""", RegexOption.IGNORE_CASE)

    /**
     * A host string a preview may fetch. IP literals never are; neither is a single label. A trailing
     * dot and a host of only numeric or hex labels (`127.1`, `0x7f.0.0.1`) are refused too, because
     * they are how a literal or `localhost` hides from the rules above (parity decision D13).
     */
    fun isEligibleHost(host: String): Boolean {
        val h = host.lowercase()
        if (h.startsWith("[") || ':' in h || ipv4.matches(h)) return false
        if (h.endsWith(".") || h.split('.').all { numericLabel.matches(it) }) return false
        if (h == "localhost" || blockedSuffixes.any { h.endsWith(it) }) return false
        return '.' in h
    }

    /**
     * Whether [url] writes its host with a percent escape, as in `https://ex%61mple.com/`. `HttpUrl`
     * decodes the escape and would fetch the decoded host, while iOS keeps it, so both apps give
     * such a link no card (parity decision D11).
     */
    fun hasEscapedHost(url: String): Boolean {
        val authority = url.substringAfter("://", missingDelimiterValue = "")
            .takeWhile { it != '/' && it != '?' && it != '#' }
        return '%' in authority.substringAfterLast('@')
    }

    /** Lowercased scheme and host, no fragment, no :443. Path and query kept as written. */
    fun cacheKey(url: String): String? {
        val u = url.toHttpUrlOrNull() ?: return null
        return u.newBuilder().fragment(null).build().toString()
    }
}
