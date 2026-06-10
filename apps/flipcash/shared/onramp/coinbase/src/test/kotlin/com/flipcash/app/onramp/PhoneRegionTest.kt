package com.flipcash.app.onramp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhoneRegionTest {

    // region NYC area codes

    @Test
    fun `212 area code returns US-NY`() {
        assertEquals(PhoneRegion("US", "NY"), regionFromPhone("+12125551234"))
    }

    @Test
    fun `718 area code returns US-NY`() {
        assertEquals(PhoneRegion("US", "NY"), regionFromPhone("+17185551234"))
    }

    @Test
    fun `917 area code returns US-NY`() {
        assertEquals(PhoneRegion("US", "NY"), regionFromPhone("+19175551234"))
    }

    @Test
    fun `646 area code returns US-NY`() {
        assertEquals(PhoneRegion("US", "NY"), regionFromPhone("+16465551234"))
    }

    @Test
    fun `347 area code returns US-NY`() {
        assertEquals(PhoneRegion("US", "NY"), regionFromPhone("+13475551234"))
    }

    @Test
    fun `929 area code returns US-NY`() {
        assertEquals(PhoneRegion("US", "NY"), regionFromPhone("+19295551234"))
    }

    // endregion

    // region US non-NYC

    @Test
    fun `non-NYC US area code returns US with no subdivision`() {
        assertEquals(PhoneRegion("US", null), regionFromPhone("+14155551234")) // SF
    }

    // endregion

    // region international

    @Test
    fun `Canadian number returns CA`() {
        assertEquals(PhoneRegion("CA", null), regionFromPhone("+14165551234")) // Toronto
    }

    @Test
    fun `UK number returns GB`() {
        assertEquals(PhoneRegion("GB", null), regionFromPhone("+442071234567"))
    }

    @Test
    fun `German number returns DE`() {
        assertEquals(PhoneRegion("DE", null), regionFromPhone("+4915112345678"))
    }

    // endregion

    // region edge cases

    @Test
    fun `invalid number returns null`() {
        assertNull(regionFromPhone("12345"))
    }

    @Test
    fun `empty string returns null`() {
        assertNull(regionFromPhone(""))
    }

    @Test
    fun `non-numeric string returns null`() {
        assertNull(regionFromPhone("not-a-phone"))
    }

    // endregion
}
