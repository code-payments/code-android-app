package com.flipcash.app.contacts

import com.flipcash.app.contacts.device.DeviceContactLookup
import com.flipcash.app.persistence.sources.ContactDataSource
import com.flipcash.app.phone.PhoneUtils
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContactResolverTest {

    private val e164 = "+15551234567"

    private fun resolver(
        dbName: String? = null,
        deviceName: String? = null,
        formatted: String? = null,
        formatThrows: Boolean = false,
        photoUri: String? = null,
    ): ContactResolver {
        val dataSource = mockk<ContactDataSource> {
            coEvery { getDisplayName(e164) } returns dbName
            coEvery { getPhotoUri(e164) } returns photoUri
        }
        val deviceLookup = mockk<DeviceContactLookup> {
            every { lookupDisplayName(e164) } returns deviceName
        }
        val phoneUtils = mockk<PhoneUtils> {
            if (formatThrows) {
                every { formatNumber(any<String>()) } throws RuntimeException("parse error")
            } else {
                every { formatNumber(any<String>()) } returns (formatted ?: e164)
            }
        }
        return ContactResolver(dataSource, deviceLookup, phoneUtils)
    }

    // region resolveName

    @Test
    fun `DB display name is returned when present`() = runTest {
        val result = resolver(dbName = "Alice").resolveName(e164)
        assertEquals("Alice", result)
    }

    @Test
    fun `device lookup is used when DB returns null`() = runTest {
        val result = resolver(deviceName = "Bob Device").resolveName(e164)
        assertEquals("Bob Device", result)
    }

    @Test
    fun `formatted number is used when DB and device both return null`() = runTest {
        val result = resolver(formatted = "+1 (555) 123-4567").resolveName(e164)
        assertEquals("+1 (555) 123-4567", result)
    }

    @Test
    fun `fallback is returned when all sources fail`() = runTest {
        val result = resolver(formatThrows = true).resolveName(e164, fallback = "unknown")
        assertEquals("unknown", result)
    }

    @Test
    fun `raw e164 is default fallback`() = runTest {
        val result = resolver(formatThrows = true).resolveName(e164)
        assertEquals(e164, result)
    }

    @Test
    fun `DB takes priority over device lookup`() = runTest {
        val result = resolver(dbName = "Alice DB", deviceName = "Alice Device").resolveName(e164)
        assertEquals("Alice DB", result)
    }

    // endregion

    // region resolvePhotoUri

    @Test
    fun `photo URI is returned from data source`() = runTest {
        val result = resolver(photoUri = "content://photo/1").resolvePhotoUri(e164)
        assertEquals("content://photo/1", result)
    }

    @Test
    fun `null photo URI when data source has none`() = runTest {
        val result = resolver().resolvePhotoUri(e164)
        assertNull(result)
    }

    // endregion
}
