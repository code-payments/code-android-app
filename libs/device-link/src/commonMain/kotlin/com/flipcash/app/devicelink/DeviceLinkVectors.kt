package com.flipcash.app.devicelink

/**
 * The link crypto with fixed keys, so a caller outside Kotlin (the SharedCoreKit Swift tests) can
 * be checked against `test-vectors/device_link.json`. These are pure functions; the real flow
 * draws fresh keys for every link and never calls them.
 */
object DeviceLinkVectors {

    /** The six-digit code the phone shows. */
    fun phoneCode(
        phonePublicKey: ByteArray,
        phoneSecretKey: ByteArray,
        desktopPublicKey: ByteArray,
        secret: ByteArray,
    ): String {
        val session = LinkCrypto.phoneSession(LinkCrypto.Ephemeral(phonePublicKey, phoneSecretKey), desktopPublicKey, secret)
        return LinkCrypto.shortCode(session).also { session.wipe() }
    }

    /** What the desktop recovers from [sealedSeed], or null if it doesn't authenticate. */
    fun desktopOpenSeed(
        desktopPublicKey: ByteArray,
        desktopSecretKey: ByteArray,
        phonePublicKey: ByteArray,
        secret: ByteArray,
        sealedSeed: ByteArray,
    ): ByteArray? {
        val session = LinkCrypto.desktopSession(LinkCrypto.Ephemeral(desktopPublicKey, desktopSecretKey), phonePublicKey, secret)
        return LinkCrypto.open(session.phoneToDesktop, sealedSeed, session.transcript).also { session.wipe() }
    }
}
