package com.flipcash.app.devicelink

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.seconds

/**
 * The phone side of a link. [connect] dials the invite's address and returns the code to compare;
 * the seed only leaves the phone through [Pending.confirm], after the user says the codes match.
 */
object PhoneLinker {
    suspend fun connect(invite: LinkInvite): Result<Pending> {
        val selector = SelectorManager(Dispatchers.Default)
        val phone = LinkCrypto.ephemeral()
        return runCatching {
            val socket = withTimeoutOrNull(10.seconds) {
                aSocket(selector).tcp().connect(InetSocketAddress(invite.host, invite.port))
            } ?: error("Couldn't reach the desktop at ${invite.host}")
            val channel = LinkChannel(socket)
            channel.send(Frame.HELLO, byteArrayOf(Frame.PROTOCOL_VERSION) + phone.publicKey)
            val session = LinkCrypto.phoneSession(phone, invite.publicKey, invite.secret)
            Pending(channel, session, selector)
        }.onFailure { selector.close() }
            .also { phone.wipe() }
    }

    class Pending internal constructor(
        private val channel: LinkChannel,
        private val session: LinkCrypto.Session,
        private val selector: SelectorManager,
    ) {
        val code: String = LinkCrypto.shortCode(session)

        /** Sends the seed and waits for the desktop to confirm it opened it. */
        suspend fun confirm(entropyB64: String): Result<Unit> = runCatching {
            val entropy = Base64.decode(entropyB64)
            try {
                channel.send(Frame.SEED, LinkCrypto.seal(session.phoneToDesktop, entropy, session.transcript))
            } finally {
                entropy.fill(0)
            }
            val ack = withTimeoutOrNull(10.seconds) { channel.receive() }
            val body = ack?.takeIf { it.first == Frame.ACK }
                ?.let { LinkCrypto.open(session.desktopToPhone, it.second, session.transcript) }
            check(body?.contentEquals(DesktopLinkHost.ACK_BODY) == true) { "The desktop didn't confirm the link" }
        }.also { close() }

        suspend fun cancel() {
            runCatching { channel.send(Frame.CANCEL) }
            close()
        }

        private fun close() {
            session.wipe()
            channel.close()
            selector.close()
        }
    }
}
