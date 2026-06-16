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
        devicePhotoUri: String? = null,
    ): ContactResolver {
        val dataSource = mockk<ContactDataSource> {
            coEvery { getDisplayName(e164) } returns dbName
            coEvery { getPhotoUri(e164) } returns photoUri
            coEvery { updateDisplayName(any(), any()) } returns Unit
            coEvery { updatePhotoUri(any(), any()) } returns Unit
        }
        val deviceLookup = mockk<DeviceContactLookup> {
            every { lookupDisplayName(e164) } returns deviceName
            every { lookupPhotoUri(e164) } returns devicePhotoUri
            every { lookupPhotoBytes(e164) } returns null
            every { lookupProfilePhoto() } returns null
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
    fun `device name is returned when present`() = runTest {
        val result = resolver(deviceName = "Alice Device").resolveName(e164)
        assertEquals("Alice Device", result)
    }

    @Test
    fun `DB name is used when device returns null`() = runTest {
        val result = resolver(dbName = "Alice DB").resolveName(e164)
        assertEquals("Alice DB", result)
    }

    @Test
    fun `formatted number is used when device and DB both return null`() = runTest {
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
    fun `device takes priority over DB name`() = runTest {
        val result = resolver(dbName = "Alice DB", deviceName = "Alice Device").resolveName(e164)
        assertEquals("Alice Device", result)
    }

    // endregion

    // region resolvePhotoUri

    @Test
    fun `photo URI is returned from data source`() = runTest {
        val result = resolver(photoUri = "content://photo/1").resolvePhotoUri(e164)
        assertEquals("content://photo/1", result)
    }

    @Test
    fun `device photo URI used when data source has none`() = runTest {
        val result = resolver(devicePhotoUri = "content://photo/2").resolvePhotoUri(e164)
        assertEquals("content://photo/2", result)
    }

    @Test
    fun `null photo URI when both sources have none`() = runTest {
        val result = resolver().resolvePhotoUri(e164)
        assertNull(result)
    }

    @Test
    fun `data source photo URI takes priority over device`() = runTest {
        val result = resolver(photoUri = "content://photo/1", devicePhotoUri = "content://photo/2")
            .resolvePhotoUri(e164)
        assertEquals("content://photo/1", result)
    }

    // endregion
}
