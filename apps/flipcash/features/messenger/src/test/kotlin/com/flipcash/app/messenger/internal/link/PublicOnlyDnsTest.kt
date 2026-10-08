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
}
