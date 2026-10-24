package com.flipcash.app.devicelink

import kotlin.io.encoding.Base64

/**
 * What the desktop's QR code carries: where to connect, the desktop's one-time public key, and a
 * one-time secret. Only a device that scanned the code knows the secret, and it is bound into every
 * key and code derived after, so a device on the LAN that finds the port without the QR can't link.
 */
class LinkInvite(
    val host: String,
    val port: Int,
    val publicKey: ByteArray,
    val secret: ByteArray,
) {
    fun encode(): String =
        "$SCHEME:$VERSION?h=$host&p=$port&k=${b64.encode(publicKey)}&s=${b64.encode(secret)}"

    companion object {
        private const val SCHEME = "flipcash-link"
        private const val VERSION = 1
        private val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

        fun parse(text: String): LinkInvite? {
            val prefix = "$SCHEME:$VERSION?"
            if (!text.startsWith(prefix)) return null
            val params = text.removePrefix(prefix).split('&').mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
            }.toMap()
            return runCatching {
                LinkInvite(
                    host = params.getValue("h").takeIf { it.isNotBlank() }!!,
                    port = params.getValue("p").toInt().takeIf { it in 1..65535 }!!,
                    publicKey = b64.decode(params.getValue("k")).takeIf { it.size == LinkCrypto.KEY_BYTES }!!,
                    secret = b64.decode(params.getValue("s")).takeIf { it.size == LinkCrypto.SECRET_BYTES }!!,
                )
            }.getOrNull()
        }
    }
}
