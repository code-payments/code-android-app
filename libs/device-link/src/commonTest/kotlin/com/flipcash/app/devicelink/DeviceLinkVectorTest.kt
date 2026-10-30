package com.flipcash.app.devicelink

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** GATE: reproduces every vector in `device_link.json` (canonical: test-vectors/device_link.json). */
class DeviceLinkVectorTest {
    private val root = Json.parseToJsonElement(readTestResource("device_link.json")).jsonObject
    private val cases = root["cases"]!!.jsonArray.map { it.jsonObject }
    private val rejects = root["rejects"]!!.jsonObject

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.hex(key: String) = str(key).hexToByteArray()

    private fun JsonObject.sessions(): Pair<LinkCrypto.Session, LinkCrypto.Session> {
        val desktop = LinkCrypto.Ephemeral(hex("desktopPublicKey"), hex("desktopSecretKey"))
        val phone = LinkCrypto.Ephemeral(hex("phonePublicKey"), hex("phoneSecretKey"))
        return LinkCrypto.phoneSession(phone, desktop.publicKey, hex("secret")) to
            LinkCrypto.desktopSession(desktop, phone.publicKey, hex("secret"))
    }

    @Test
    fun inviteEncodesAndParses() = cases.forEach { c ->
        val invite = LinkInvite(c.str("host"), c.getValue("port").jsonPrimitive.int, c.hex("desktopPublicKey"), c.hex("secret"))
        assertEquals(c.str("invite"), invite.encode(), c.str("name"))
        val parsed = assertNotNull(LinkInvite.parse(c.str("invite")), c.str("name"))
        assertEquals(c.str("host"), parsed.host)
        assertContentEquals(c.hex("desktopPublicKey"), parsed.publicKey)
        assertContentEquals(c.hex("secret"), parsed.secret)
    }

    @Test
    fun bothSidesDeriveTheVectorKeysAndCode() = cases.forEach { c ->
        val (phone, desktop) = c.sessions()
        for (s in listOf(phone, desktop)) {
            assertContentEquals(c.hex("transcript"), s.transcript, c.str("name"))
            assertContentEquals(c.hex("phoneToDesktop"), s.phoneToDesktop, c.str("name"))
            assertContentEquals(c.hex("desktopToPhone"), s.desktopToPhone, c.str("name"))
            assertEquals(c.str("code"), LinkCrypto.shortCode(s), c.str("name"))
        }
    }

    @Test
    fun sealedFramesMatchAndOpen() = cases.forEach { c ->
        val (phone, desktop) = c.sessions()
        val seed = c.hex("sealedSeed")
        val ack = c.hex("sealedAck")
        assertContentEquals(seed, LinkCrypto.seal(phone.phoneToDesktop, c.hex("entropy"), phone.transcript, seed.copyOf(24)))
        assertContentEquals(ack, LinkCrypto.seal(desktop.desktopToPhone, DesktopLinkHost.ACK_BODY, desktop.transcript, ack.copyOf(24)))
        val opened = assertNotNull(LinkCrypto.open(desktop.phoneToDesktop, seed, desktop.transcript))
        assertEquals(c.str("entropyBase64"), Base64.encode(opened))
        assertContentEquals(DesktopLinkHost.ACK_BODY, LinkCrypto.open(phone.desktopToPhone, ack, phone.transcript))
    }

    @Test
    fun theSwiftFacingEntryPointsMatchTheVectors() = cases.forEach { c ->
        assertEquals(
            c.str("code"),
            DeviceLinkVectors.phoneCode(c.hex("phonePublicKey"), c.hex("phoneSecretKey"), c.hex("desktopPublicKey"), c.hex("secret")),
        )
        val opened = DeviceLinkVectors.desktopOpenSeed(
            c.hex("desktopPublicKey"), c.hex("desktopSecretKey"), c.hex("phonePublicKey"), c.hex("secret"), c.hex("sealedSeed"),
        )
        assertContentEquals(c.hex("entropy"), opened)
    }

    @Test
    fun rejectsTamperedSeeds() {
        val c = cases.first()
        val (_, desktop) = c.sessions()
        rejects.getValue("seed").jsonArray.map { it.jsonObject }.forEach { r ->
            val transcript = r["transcript"]?.let { r.hex("transcript") } ?: desktop.transcript
            assertNull(LinkCrypto.open(desktop.phoneToDesktop, r.hex("sealedSeed"), transcript), r.str("why"))
        }
    }

    @Test
    fun rejectsBadInvites() = rejects.getValue("invite").jsonArray.forEach {
        assertNull(LinkInvite.parse(it.jsonPrimitive.content), it.jsonPrimitive.content)
    }
}
