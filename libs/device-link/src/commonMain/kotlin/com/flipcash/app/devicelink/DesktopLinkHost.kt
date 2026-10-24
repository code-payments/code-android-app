package com.flipcash.app.devicelink

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The desktop side of a link: listens on [host], shows the invite, takes one connection, and
 * emits the seed once the phone sends it sealed under the session key. Each collection is one
 * attempt with fresh keys; the listener closes after the first connection.
 */
class DesktopLinkHost(
    private val host: String,
    private val bindHost: String = "0.0.0.0",
    private val timeout: Duration = 5.minutes,
) {
    sealed interface State {
        data class Waiting(val invite: String) : State
        data class Confirm(val code: String) : State
        /** [entropyB64] is the seed; the caller stores it and signs in. */
        class Linked(val entropyB64: String) : State
        data class Failed(val reason: String) : State
    }

    fun link(): Flow<State> = flow {
        val desktop = LinkCrypto.ephemeral()
        val secret = LinkCrypto.secret()
        val selector = SelectorManager(Dispatchers.Default)
        var session: LinkCrypto.Session? = null
        try {
            val server = aSocket(selector).tcp().bind(InetSocketAddress(bindHost, 0))
            val port = (server.localAddress as InetSocketAddress).port
            emit(State.Waiting(LinkInvite(host, port, desktop.publicKey, secret).encode()))

            val socket = withTimeoutOrNull(timeout) { server.accept() }
            server.close()
            if (socket == null) return@flow emit(State.Failed("Timed out waiting for the phone"))
            val channel = LinkChannel(socket)
            try {
                val hello = channel.receive()
                if (hello == null || hello.first != Frame.HELLO || hello.second.size != 1 + LinkCrypto.KEY_BYTES ||
                    hello.second[0] != Frame.PROTOCOL_VERSION
                ) {
                    return@flow emit(State.Failed("The phone sent an unexpected message"))
                }
                val phoneKey = hello.second.copyOfRange(1, hello.second.size)
                val s = LinkCrypto.desktopSession(desktop, phoneKey, secret).also { session = it }
                emit(State.Confirm(LinkCrypto.shortCode(s)))

                val reply = withTimeoutOrNull(timeout) { channel.receive() }
                when {
                    reply == null -> emit(State.Failed("The phone disconnected"))
                    reply.first == Frame.CANCEL -> emit(State.Failed("Cancelled on the phone"))
                    reply.first != Frame.SEED -> emit(State.Failed("The phone sent an unexpected message"))
                    else -> {
                        val entropy = LinkCrypto.open(s.phoneToDesktop, reply.second, s.transcript)
                        if (entropy == null || entropy.size !in ENTROPY_SIZES) {
                            emit(State.Failed("Couldn't verify the phone's message"))
                        } else {
                            channel.send(Frame.ACK, LinkCrypto.seal(s.desktopToPhone, ACK_BODY, s.transcript))
                            val b64 = Base64.encode(entropy)
                            entropy.fill(0)
                            emit(State.Linked(b64))
                        }
                    }
                }
            } finally {
                channel.close()
            }
        } finally {
            desktop.wipe()
            session?.wipe()
            selector.close()
        }
    }

    internal companion object {
        val ENTROPY_SIZES = setOf(16, 32)
        val ACK_BODY = "linked".encodeToByteArray()
    }
}
