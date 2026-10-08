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

    /**
     * D14 ranges are pinned by the fixture's `addresses` rows. This covers what those rows leave out:
     * the edges of each block, and the mapped forms Java may hand back as an `Inet4Address`.
     */
    @Test
    fun `range edges and java mapped forms the fixture leaves out`() {
        for (a in listOf("64:ff9b::7f00:1", "192.0.0.255", "2002::1", "::ffff:192.0.2.1")) {
            assertTrue(InetAddress.getByName(a).isPrivate(), a)
        }
        for (a in listOf("2001:db8::a00:1", "192.0.1.1", "198.17.255.255", "223.255.255.255", "2001:4860::8888", "2003::1")) {
            assertFalse(InetAddress.getByName(a).isPrivate(), a)
        }
        val mapped = ByteArray(16).also { it[10] = 0xFF.toByte(); it[11] = 0xFF.toByte(); it[12] = 10; it[15] = 1 }
        assertTrue(InetAddress.getByAddress(mapped).isPrivate())
        mapped[12] = 8; mapped[13] = 8; mapped[14] = 8; mapped[15] = 8
        assertFalse(InetAddress.getByAddress(mapped).isPrivate())
    }
}
