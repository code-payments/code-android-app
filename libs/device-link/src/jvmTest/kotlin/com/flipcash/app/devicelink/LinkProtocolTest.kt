package com.flipcash.app.devicelink

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.io.encoding.Base64
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Both sides of a link over a real loopback socket. The seed is random bytes, never a real account's. */
class LinkProtocolTest {
    private val seed = Base64.encode(Random.nextBytes(16))

    /** Runs the desktop side, hands its invite to [phone], and returns every state the desktop emitted. */
    private fun runLink(phone: suspend (LinkInvite) -> Unit): List<DesktopLinkHost.State> = runBlocking {
        withTimeout(20_000) {
            val states = mutableListOf<DesktopLinkHost.State>()
            val invites = Channel<String>(1)
            val desktop = launch(Dispatchers.Default) {
                DesktopLinkHost(host = "127.0.0.1", bindHost = "127.0.0.1").link()
                    .onEach { states += it; if (it is DesktopLinkHost.State.Waiting) invites.send(it.invite) }
                    .collect()
            }
            phone(LinkInvite.parse(invites.receive())!!)
            desktop.join()
            states
        }
    }

    @Test
    fun `codes match and the seed arrives`() {
        var phoneCode: String? = null
        val states = runLink { invite ->
            val pending = PhoneLinker.connect(invite).getOrThrow()
            phoneCode = pending.code
            pending.confirm(seed).getOrThrow()
        }
        val confirm = states.filterIsInstance<DesktopLinkHost.State.Confirm>().single()
        assertEquals(phoneCode, confirm.code)
        assertTrue(confirm.code.matches(Regex("\\d{6}")))
        assertEquals(seed, assertIs<DesktopLinkHost.State.Linked>(states.last()).entropyB64)
    }

    @Test
    fun `cancelling on the phone sends nothing`() {
        val states = runLink { invite -> PhoneLinker.connect(invite).getOrThrow().cancel() }
        assertEquals(DesktopLinkHost.State.Failed("Cancelled on the phone"), states.last())
    }

    @Test
    fun `a swapped phone key changes the desktop's code`() {
        // A man in the middle replaces the phone's key on the way to the desktop with one of its own.
        var phoneCode: String? = null
        val states = runLink { invite ->
            val phone = LinkCrypto.ephemeral()
            phoneCode = LinkCrypto.shortCode(LinkCrypto.phoneSession(phone, invite.publicKey, invite.secret))
            val attacker = LinkCrypto.ephemeral()
            val selector = SelectorManager(Dispatchers.Default)
            val channel = LinkChannel(aSocket(selector).tcp().connect(InetSocketAddress(invite.host, invite.port)))
            channel.send(Frame.HELLO, byteArrayOf(Frame.PROTOCOL_VERSION) + attacker.publicKey)
            // The phone, having seen a matching-looking prompt, would seal under its own session.
            val session = LinkCrypto.phoneSession(phone, invite.publicKey, invite.secret)
            channel.send(Frame.SEED, LinkCrypto.seal(session.phoneToDesktop, Base64.decode(seed), session.transcript))
            channel.receive()
            channel.close()
            selector.close()
        }
        assertNotEquals(phoneCode, states.filterIsInstance<DesktopLinkHost.State.Confirm>().single().code)
        assertEquals(DesktopLinkHost.State.Failed("Couldn't verify the phone's message"), states.last())
    }

    @Test
    fun `tampered seed is rejected`() {
        val states = runLink { invite ->
            val phone = LinkCrypto.ephemeral()
            val session = LinkCrypto.phoneSession(phone, invite.publicKey, invite.secret)
            val selector = SelectorManager(Dispatchers.Default)
            val channel = LinkChannel(aSocket(selector).tcp().connect(InetSocketAddress(invite.host, invite.port)))
            channel.send(Frame.HELLO, byteArrayOf(Frame.PROTOCOL_VERSION) + phone.publicKey)
            val sealed = LinkCrypto.seal(session.phoneToDesktop, Base64.decode(seed), session.transcript)
            sealed[sealed.size - 1] = (sealed.last().toInt() xor 1).toByte()
            channel.send(Frame.SEED, sealed)
            channel.receive()
            channel.close()
            selector.close()
        }
        assertEquals(DesktopLinkHost.State.Failed("Couldn't verify the phone's message"), states.last())
    }

    @Test
    fun `a device without the QR secret can't link`() {
        val states = runLink { invite ->
            val pending = PhoneLinker.connect(
                LinkInvite(invite.host, invite.port, invite.publicKey, LinkCrypto.secret()),
            ).getOrThrow()
            assertTrue(pending.confirm(seed).isFailure)
        }
        assertIs<DesktopLinkHost.State.Failed>(states.last())
    }

    @Test
    fun `invite round-trips and rejects junk`() {
        val invite = LinkInvite("192.168.1.20", 50123, ByteArray(32) { it.toByte() }, ByteArray(16) { 7 })
        val parsed = LinkInvite.parse(invite.encode())!!
        assertEquals(invite.host, parsed.host)
        assertEquals(invite.port, parsed.port)
        assertContentEquals(invite.publicKey, parsed.publicKey)
        assertContentEquals(invite.secret, parsed.secret)
        assertNull(LinkInvite.parse("https://example.com"))
        assertNull(LinkInvite.parse(invite.encode().replace("p=50123", "p=0")))
    }

    @Test
    fun `the phone controller reads the seed only after confirm`() {
        var seedReads = 0
        val states = runLink { invite ->
            kotlinx.coroutines.coroutineScope {
                val controller = PhoneLinkController(this) { seedReads++; seed }
                controller.onScanned(invite.encode())
                val confirm = withTimeout(10_000) {
                    controller.state.first { it is PhoneLinkController.State.Confirm }
                }
                assertIs<PhoneLinkController.State.Confirm>(confirm)
                assertEquals(0, seedReads)
                controller.confirm()
                withTimeout(10_000) { controller.state.first { it == PhoneLinkController.State.Linked } }
            }
        }
        assertEquals(1, seedReads)
        assertEquals(seed, assertIs<DesktopLinkHost.State.Linked>(states.last()).entropyB64)
    }
}
