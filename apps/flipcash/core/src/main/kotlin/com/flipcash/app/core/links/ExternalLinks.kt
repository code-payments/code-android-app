package com.flipcash.app.core.links

import android.content.Context
import androidx.compose.ui.platform.UriHandler
import com.flipcash.core.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarCheckbox
import com.getcode.manager.BottomBarManager
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

    /** The host is exactly one the user chose to trust from the warning; open without asking. */
    data class Trusted(val host: String) : LinkDestination

    /**
     * Anywhere else. [host] is what the warning shows: lowercased and in ASCII (punycode) form, so
     * a homograph domain can't pass for the real one. For a link with no host at all (`mailto:`),
     * it is the scheme, and [trustable] is false: there is no website to stop asking about.
     */
    data class External(val host: String, val trustable: Boolean = true) : LinkDestination
}

/**
 * Classifies [url] by its host, on an exact match against [FIRST_PARTY_HOSTS] and then
 * [trustedHosts] — never a suffix match, so `evilflipcash.com` and `flipcash.com.evil.tld` both
 * warn, and trusting `x.com` does not cover `mail.x.com`. [trustedHosts] holds hosts in the form
 * [LinkDestination.External.host] gives them.
 *
 * The host is parsed here rather than with `Uri`/`URI`: those differ from browsers on inputs an
 * attacker picks (`\` as a path separator, non-ASCII hosts), and the answer has to agree with where
 * the browser actually goes. Anything this can't read as first-party warns.
 */
fun classifyLink(url: String, trustedHosts: Set<String> = emptySet()): LinkDestination {
    val raw = hostOf(url.trim())
    if (raw == null) {
        val scheme = url.trim().substringBefore(':', missingDelimiterValue = "").lowercase()
        return LinkDestination.External(
            host = scheme.ifEmpty { url.trim().take(MAX_FALLBACK_LENGTH) },
            trustable = false,
        )
    }
    val host = asciiHost(raw)
    return when (host) {
        in FIRST_PARTY_HOSTS -> LinkDestination.FirstParty
        in trustedHosts -> LinkDestination.Trusted(host)
        else -> LinkDestination.External(host)
    }
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
 * Runs [open] straight away for a first-party or trusted link, and otherwise asks first: "You're
 * Leaving Flipcash", naming the host, with Open Website as the primary button and Cancel as the
 * secondary. Above the button sits "Don't ask again for <host>", unchecked; [trusted] keeps the host
 * only when Open Website is tapped with it checked.
 *
 * A masked link, whose text is a [label] rather than its address, says so in the subtitle:
 * "<label>" will open <host>. A bare link keeps the plain subtitle.
 *
 * Only for links someone else wrote, such as a chat message. A link the app opens on purpose
 * (terms, a token's socials) goes straight to the browser.
 */
fun openWithExternalLinkCheck(
    context: Context,
    url: String,
    trusted: TrustedWebsites,
    label: String? = null,
    open: () -> Unit,
) {
    val trustedHosts = trusted.websites.value.mapTo(mutableSetOf()) { it.host }
    when (val destination = classifyLink(url, trustedHosts)) {
        LinkDestination.FirstParty,
        is LinkDestination.Trusted -> open()
        is LinkDestination.External -> BottomBarManager.showMessage(
            externalLinkWarning(
                destination = destination,
                title = context.getString(R.string.prompt_title_externalLink),
                message = if (label == null) {
                    context.getString(R.string.prompt_description_externalLink, destination.host)
                } else {
                    context.getString(R.string.prompt_description_externalLinkMasked, label, destination.host)
                },
                openWebsite = context.getString(R.string.action_openWebsite),
                dontAskAgain = context.getString(R.string.action_dontAskAgainForHost, destination.host),
                onTrust = trusted::trust,
                open = open,
            )
        )
    }
}

/**
 * The warning for [destination]. Separate from [openWithExternalLinkCheck] so the rule for when
 * [onTrust] runs can be tested without resources: only from Open Website, and only with the box
 * checked at that moment.
 */
internal fun externalLinkWarning(
    destination: LinkDestination.External,
    title: String,
    message: String,
    openWebsite: String,
    dontAskAgain: String,
    onTrust: (host: String) -> Unit,
    open: () -> Unit,
): BottomBarManager.BottomBarMessage {
    var dontAsk = false
    return BottomBarManager.BottomBarMessage(
        title = title,
        subtitle = message,
        type = BottomBarManager.BottomBarMessageType.INFO,
        checkbox = BottomBarCheckbox(
            label = dontAskAgain,
            onCheckedChange = { dontAsk = it },
        ).takeIf { destination.trustable },
        actions = listOf(
            BottomBarAction(
                text = openWebsite,
                onClick = {
                    if (dontAsk) onTrust(destination.host)
                    open()
                },
            ),
        ),
        showCancel = true,
    )
}

/**
 * A `LocalUriHandler` that runs [openWithExternalLinkCheck] before [delegate] opens the link.
 * Provided around the chat transcript, so the `LinkAnnotation.Url` spans in message text pass
 * through it.
 */
class ExternalLinkUriHandler(
    private val context: Context,
    private val delegate: UriHandler,
    private val trusted: TrustedWebsites,
) : UriHandler {
    override fun openUri(uri: String) {
        openWithExternalLinkCheck(context, uri, trusted) { delegate.openUri(uri) }
    }

    /** A masked link: the warning names [label] as well as the host it really opens. */
    fun openMasked(label: String, uri: String) {
        openWithExternalLinkCheck(context, uri, trusted, label) { delegate.openUri(uri) }
    }
}
