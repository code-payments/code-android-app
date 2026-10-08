package com.flipcash.app.core.links

import com.getcode.manager.SelectedBottomBarAction
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TrustedLinksTest {

    @Test
    fun `a trusted host opens without a warning`() {
        assertEquals(LinkDestination.Trusted("x.com"), classifyLink("https://x.com/flipcash", setOf("x.com")))
        assertEquals(LinkDestination.Trusted("x.com"), classifyLink("HTTPS://X.com:443/", setOf("x.com")))
    }

    @Test
    fun `trusting a host does not cover its subdomains or lookalikes`() {
        val trusted = setOf("x.com")
        assertEquals(LinkDestination.External("mail.x.com"), classifyLink("https://mail.x.com", trusted))
        assertEquals(LinkDestination.External("x.com.evil.tld"), classifyLink("https://x.com.evil.tld", trusted))
        assertEquals(LinkDestination.External("evilx.com"), classifyLink("https://evilx.com", trusted))
    }

    @Test
    fun `a trusted parent does not cover its own subdomain the other way round`() {
        assertEquals(LinkDestination.External("x.com"), classifyLink("https://x.com", setOf("mail.x.com")))
    }

    @Test
    fun `a homograph is matched on its punycode, not on how it looks`() {
        // Cyrillic "а" in place of the Latin one.
        val homograph = "https://flipcаsh.io/"
        val ascii = (classifyLink(homograph) as LinkDestination.External).host
        assertEquals(LinkDestination.External(ascii), classifyLink(homograph, setOf("flipcash.io")))
        assertEquals(LinkDestination.Trusted(ascii), classifyLink(homograph, setOf(ascii)))
    }

    @Test
    fun `first-party hosts keep their own handling`() {
        assertEquals(LinkDestination.FirstParty, classifyLink("https://flipcash.com", setOf("flipcash.com")))
    }

    @Test
    fun `a link with no host is never offered for trust`() {
        val warning = warning(LinkDestination.External("mailto", trustable = false))
        assertNull(warning.message.checkbox)
    }

    @Test
    fun `open website with the box checked trusts the host and opens`() {
        val warning = warning()
        assertNotNull(warning.message.checkbox).onCheckedChange(true)
        warning.message.actions.single().onClick()

        assertEquals(listOf("x.com"), warning.trusted)
        assertEquals(1, warning.opened)
    }

    @Test
    fun `open website with the box unchecked opens without trusting`() {
        val warning = warning()
        warning.message.actions.single().onClick()

        assertEquals(emptyList(), warning.trusted)
        assertEquals(1, warning.opened)
    }

    @Test
    fun `unticking before open website saves nothing`() {
        val warning = warning()
        val checkbox = assertNotNull(warning.message.checkbox)
        checkbox.onCheckedChange(true)
        checkbox.onCheckedChange(false)
        warning.message.actions.single().onClick()

        assertEquals(emptyList(), warning.trusted)
    }

    @Test
    fun `cancel or dismiss with the box checked saves nothing`() {
        val warning = warning()
        assertNotNull(warning.message.checkbox).onCheckedChange(true)
        warning.message.onClose(SelectedBottomBarAction(-1))

        assertEquals(emptyList(), warning.trusted)
        assertEquals(0, warning.opened)
    }

    @Test
    fun `each warning starts unchecked`() {
        val first = warning()
        assertNotNull(first.message.checkbox).onCheckedChange(true)

        val second = warning()
        second.message.actions.single().onClick()

        assertEquals(emptyList(), second.trusted)
    }

    private class Warning(destination: LinkDestination.External) {
        val trusted = mutableListOf<String>()
        var opened = 0
        val message = externalLinkWarning(
            destination = destination,
            title = "You're Leaving Flipcash",
            message = "This will open ${destination.host}.",
            openWebsite = "Open Website",
            dontAskAgain = "Don't ask again for ${destination.host}",
            onTrust = { trusted += it },
            open = { opened++ },
        )
    }

    private fun warning(destination: LinkDestination.External = LinkDestination.External("x.com")) =
        Warning(destination)
}
