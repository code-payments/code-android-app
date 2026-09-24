package com.flipcash.app.core.links

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalLinksTest {

    @Test
    fun `every first-party host opens without a warning`() {
        FIRST_PARTY_HOSTS.forEach { host ->
            assertEquals(LinkDestination.FirstParty, classifyLink("https://$host/c/#/e=abc"), host)
        }
    }

    @Test
    fun `first-party match ignores case, port, userinfo and scheme`() {
        listOf(
            "HTTPS://Send.FlipCash.com/c/#/e=abc",
            "https://flipcash.com:443/download",
            "https://user@app.flipcash.com/token/x",
            "http://www.flipcash.com",
            "flipcash.com/download",
        ).forEach { assertEquals(LinkDestination.FirstParty, classifyLink(it), it) }
    }

    @Test
    fun `a lookalike host warns`() {
        assertEquals(LinkDestination.External("evilflipcash.com"), classifyLink("https://evilflipcash.com/"))
        assertEquals(
            LinkDestination.External("flipcash.com.evil.tld"),
            classifyLink("https://flipcash.com.evil.tld/c/#/e=abc"),
        )
        assertEquals(LinkDestination.External("evil.flipcash.com"), classifyLink("https://evil.flipcash.com"))
    }

    @Test
    fun `the host after userinfo is the one checked`() {
        assertEquals(LinkDestination.External("evil.com"), classifyLink("https://flipcash.com@evil.com/"))
    }

    @Test
    fun `a backslash ends the authority, as it does in a browser`() {
        assertEquals(LinkDestination.FirstParty, classifyLink("https://flipcash.com\\@evil.com"))
        assertEquals(LinkDestination.External("evil.com"), classifyLink("https://evil.com\\@flipcash.com"))
    }

    @Test
    fun `a unicode host warns and is shown as punycode`() {
        // Cyrillic "а" in place of the Latin "a".
        val destination = classifyLink("https://flipc\u0430sh.com/login")
        assertEquals(LinkDestination.External("xn--flipcsh-6fg.com"), destination)
        destination as LinkDestination.External
        assertTrue(destination.host.all { it.code < 0x80 }, "host must be ASCII: ${destination.host}")
    }

    @Test
    fun `a punycode host warns and stays as written`() {
        assertEquals(
            LinkDestination.External("xn--flipcsh-6fg.com"),
            classifyLink("https://XN--FLIPCSH-6FG.com/"),
        )
    }

    @Test
    fun `only the host is shown, never the path or query`() {
        assertEquals(
            LinkDestination.External("x.com"),
            classifyLink("https://X.com/flipcash?ref=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa#top"),
        )
    }

    @Test
    fun `a link with no host warns with its scheme`() {
        assertEquals(LinkDestination.External("mailto"), classifyLink("mailto:someone@flipcash.com"))
    }
}
