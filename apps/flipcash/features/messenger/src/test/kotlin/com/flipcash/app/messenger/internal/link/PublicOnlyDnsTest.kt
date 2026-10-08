package com.flipcash.app.messenger.internal.link

import okhttp3.Dns
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The `addresses` vectors of `link_metadata.json`. A failure is fixed in the canonical fixture, never here. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PublicOnlyDnsTest {

    private fun fixture(): JSONObject = JSONObject(
        javaClass.classLoader!!
            .getResourceAsStream("link_metadata.json")!!
            .bufferedReader().use { it.readText() }
    )

    @Test
    fun `addresses match the cross-platform fixture`() {
        val rows = fixture().getJSONArray("addresses")
        assertTrue(rows.length() > 0)
        val failures = mutableListOf<String>()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val address = row.getString("address")
            val actual = runCatching { InetAddress.getByName(address).isPrivate() }
            when {
                actual.isFailure -> failures += "$address (${row.getString("note")}): threw ${actual.exceptionOrNull()}"
                actual.getOrNull() == row.getBoolean("public") ->
                    failures += "$address (${row.getString("note")}): expected public=${row.getBoolean("public")} but isPrivate=${actual.getOrNull()}"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private fun dnsOf(vararg literals: String) = Dns { literals.map { InetAddress.getByName(it) } }

    @Test
    fun `private answers are filtered out`() {
        val answers = PublicOnlyDns(dnsOf("10.0.0.1", "93.184.215.14", "127.0.0.1")).lookup("example.com")
        assertEquals(listOf(InetAddress.getByName("93.184.215.14")), answers)
    }

    @Test
    fun `an answer with nothing left throws`() {
        assertFailsWith<UnknownHostException> {
            PublicOnlyDns(dnsOf("10.0.0.1", "169.254.169.254")).lookup("example.com")
        }
    }

    /** `64:ff9b::/96` carries an IPv4 address in its last 32 bits; it is judged by that address (parity decision D14). */
    @Test
    fun `a nat64 address is judged by its embedded ipv4`() {
        assertTrue(InetAddress.getByName("64:ff9b::a00:1").isPrivate())
        assertTrue(InetAddress.getByName("64:ff9b::7f00:1").isPrivate())
        assertTrue(InetAddress.getByName("64:ff9b::a9fe:a9fe").isPrivate())
        assertFalse(InetAddress.getByName("64:ff9b::808:808").isPrivate())
        assertFalse(InetAddress.getByName("2001:db8::a00:1").isPrivate())
    }

    /** Special-use IPv4 blocks beyond the fixture's rows (parity decision D14). */
    @Test
    fun `special-use ipv4 ranges are private and their neighbours are not`() {
        for (a in listOf("192.0.0.1", "192.0.0.255", "192.0.2.1", "198.18.0.1", "198.19.255.254", "240.0.0.1", "255.255.255.255")) {
            assertTrue(InetAddress.getByName(a).isPrivate(), a)
        }
        for (a in listOf("192.0.1.1", "192.0.3.1", "198.17.255.255", "198.20.0.1", "239.255.255.255".let { "223.255.255.255" })) {
            assertFalse(InetAddress.getByName(a).isPrivate(), a)
        }
    }

    @Test
    fun `6to4, ipv4-compatible and mapped ipv6 are judged as the spec says`() {
        for (a in listOf("2002::1", "2002:808:808::1", "::", "::1", "::a00:1", "::808:808", "::ffff:10.0.0.1", "::ffff:192.0.2.1")) {
            assertTrue(InetAddress.getByName(a).isPrivate(), a)
        }
        for (a in listOf("::ffff:8.8.8.8", "2001:4860::8888", "2003::1")) {
            assertFalse(InetAddress.getByName(a).isPrivate(), a)
        }
        // Built from raw bytes, so a mapped form is covered whichever class Java returns.
        val mapped = ByteArray(16).also { it[10] = 0xFF.toByte(); it[11] = 0xFF.toByte(); it[12] = 10; it[15] = 1 }
        assertTrue(InetAddress.getByAddress(mapped).isPrivate())
        mapped[12] = 8; mapped[13] = 8; mapped[14] = 8; mapped[15] = 8
        assertFalse(InetAddress.getByAddress(mapped).isPrivate())
    }
}
