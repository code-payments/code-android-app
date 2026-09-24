package com.flipcash.app.core.links

import android.content.Context
import androidx.compose.ui.platform.UriHandler
import com.flipcash.core.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.R as ResourcesR
import java.net.IDN

/**
 * The hosts that are Flipcash's own: the union of Android's manifest intent filters and iOS's
 * associated-domains entitlement. The chat card classifier and the external-link warning both
 * read this one list.
 */
val FIRST_PARTY_HOSTS: Set<String> = setOf(
    "app.flipcash.com",
    "send.flipcash.com",
    "flipcash.com",
    "www.flipcash.com",
    "jump.flipcash.com",
)

/** Where a link goes, as far as the external-link warning is concerned. */
sealed interface LinkDestination {
    /** The host is exactly one of [FIRST_PARTY_HOSTS]; open without asking. */
    data object FirstParty : LinkDestination

    /**
     * Anywhere else. [host] is what the warning shows: lowercased and in ASCII (punycode) form, so
     * a homograph domain can't pass for the real one. For a link with no host at all (`mailto:`),
     * it is the scheme.
     */
    data class External(val host: String) : LinkDestination
}

/**
 * Classifies [url] by its host, on an exact match against [FIRST_PARTY_HOSTS] — never a suffix
 * match, so `evilflipcash.com` and `flipcash.com.evil.tld` both warn.
 *
 * The host is parsed here rather than with `Uri`/`URI`: those differ from browsers on inputs an
 * attacker picks (`\` as a path separator, non-ASCII hosts), and the answer has to agree with where
 * the browser actually goes. Anything this can't read as first-party warns.
 */
fun classifyLink(url: String): LinkDestination {
    val raw = hostOf(url.trim())
    if (raw == null) {
        val scheme = url.trim().substringBefore(':', missingDelimiterValue = "").lowercase()
        return LinkDestination.External(scheme.ifEmpty { url.trim().take(MAX_FALLBACK_LENGTH) })
    }
    val host = asciiHost(raw)
    return if (host in FIRST_PARTY_HOSTS) LinkDestination.FirstParty else LinkDestination.External(host)
}

private const val MAX_FALLBACK_LENGTH = 64

/**
 * The authority's host, or null when the URL has none. `scheme://[userinfo@]host[:port]`, with
 * `/`, `\`, `?` and `#` ending the authority — `\` because browsers treat it as `/` on http(s),
 * which is what makes `https://flipcash.com\@evil.com` land on `flipcash.com`.
 */
private fun hostOf(url: String): String? {
    val schemeEnd = url.indexOf("://")
    val afterScheme = when {
        schemeEnd >= 0 -> url.substring(schemeEnd + 3)
        url.startsWith("//") -> url.substring(2)
        // `mailto:x`, `tel:1` — a scheme with no authority.
        SCHEME_ONLY.containsMatchIn(url) -> return null
        // A bare `x.com/path`, as link detection can hand over.
        else -> url
    }
    val authority = afterScheme.takeWhile { it !in AUTHORITY_TERMINATORS }
    val hostAndPort = authority.substringAfterLast('@')
    val host = if (hostAndPort.startsWith("[")) {
        hostAndPort.substringBefore(']') + "]"
    } else {
        hostAndPort.substringBefore(':')
    }
    return host.takeIf { it.isNotBlank() }
}

private val SCHEME_ONLY = Regex("^[A-Za-z][A-Za-z0-9+.-]*:(?!//)")
private val AUTHORITY_TERMINATORS = setOf('/', '\\', '?', '#')

private fun asciiHost(host: String): String {
    val ascii = runCatching { IDN.toASCII(host, IDN.ALLOW_UNASSIGNED) }
        .getOrElse {
            // Not a valid IDN; show it with any non-ASCII byte escaped rather than as it looks.
            host.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
                val c = byte.toInt() and 0xFF
                if (c < 0x80) c.toChar().toString() else "%%%02X".format(c)
            }
        }
    return ascii.lowercase()
}

/**
 * Runs [open] straight away for a first-party link, and otherwise asks first: "You're leaving
 * Flipcash", naming the host, with Cancel as the primary button and Open Link as the secondary.
 *
 * Only for links someone else wrote, such as a chat message. A link the app opens on purpose
 * (terms, a token's socials) goes straight to the browser.
 */
fun openWithExternalLinkCheck(context: Context, url: String, open: () -> Unit) {
    when (val destination = classifyLink(url)) {
        LinkDestination.FirstParty -> open()
        is LinkDestination.External -> BottomBarManager.showAlert(
            title = context.getString(R.string.prompt_title_externalLink),
            message = context.getString(R.string.prompt_description_externalLink, destination.host),
            actions = listOf(
                BottomBarAction(
                    text = context.getString(ResourcesR.string.action_cancel),
                    style = BottomBarManager.BottomBarButtonStyle.Filled,
                ),
                BottomBarAction(
                    text = context.getString(R.string.action_openLink),
                    style = BottomBarManager.BottomBarButtonStyle.Text,
                    onClick = open,
                ),
            ),
        )
    }
}

/**
 * A `LocalUriHandler` that runs [openWithExternalLinkCheck] before [delegate] opens the link.
 * Provided around the chat transcript, so the `LinkAnnotation.Url` spans in message text pass
 * through it.
 */
class ExternalLinkUriHandler(
    private val context: Context,
    private val delegate: UriHandler,
) : UriHandler {
    override fun openUri(uri: String) {
        openWithExternalLinkCheck(context, uri) { delegate.openUri(uri) }
    }
}
