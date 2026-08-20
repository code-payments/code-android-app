package com.flipcash.app.onramp

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Robolectric, because the Android libphonenumber port loads its metadata from the AAR's assets and
// so needs a real Context. These cases are unchanged from when this resolved via Google's desktop
// artifact — they are the parity check that the port agrees with it on every number here.
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PhoneRegionTest {

    private lateinit var resolver: PhoneRegionResolver

    @BeforeTest
    fun setup() {
        resolver = PhoneRegionResolver(RuntimeEnvironment.getApplication())
    }

    // region NYC area codes

    @Test
    fun `212 area code returns US-NY`() {
        assertEquals(PhoneRegion("US", "NY"), resolver.regionFromPhone("+12125551234"))
    }

    @Test
    fun `718 area code returns US-NY`() {
        assertEquals(PhoneRegion("US", "NY"), resolver.regionFromPhone("+17185551234"))
    }

    @Test
    fun `917 area code returns US-NY`() {
        assertEquals(PhoneRegion("US", "NY"), resolver.regionFromPhone("+19175551234"))
    }

    @Test
    fun `646 area code returns US-NY`() {
        assertEquals(PhoneRegion("US", "NY"), resolver.regionFromPhone("+16465551234"))
    }

    @Test
    fun `347 area code returns US-NY`() {
        assertEquals(PhoneRegion("US", "NY"), resolver.regionFromPhone("+13475551234"))
    }

    @Test
    fun `929 area code returns US-NY`() {
        assertEquals(PhoneRegion("US", "NY"), resolver.regionFromPhone("+19295551234"))
    }

    // endregion

    // region US non-NYC

    @Test
    fun `non-NYC US area code returns US with no subdivision`() {
        assertEquals(PhoneRegion("US", null), resolver.regionFromPhone("+14155551234")) // SF
    }

    // endregion

    // region international

    @Test
    fun `Canadian number returns CA`() {
        assertEquals(PhoneRegion("CA", null), resolver.regionFromPhone("+14165551234")) // Toronto
    }

    @Test
    fun `UK number returns GB`() {
        assertEquals(PhoneRegion("GB", null), resolver.regionFromPhone("+442071234567"))
    }

    @Test
    fun `German number returns DE`() {
        assertEquals(PhoneRegion("DE", null), resolver.regionFromPhone("+4915112345678"))
    }

    // endregion

    // region edge cases

    @Test
    fun `invalid number returns null`() {
        assertNull(resolver.regionFromPhone("12345"))
    }

    @Test
    fun `empty string returns null`() {
        assertNull(resolver.regionFromPhone(""))
    }

    @Test
    fun `non-numeric string returns null`() {
        assertNull(resolver.regionFromPhone("not-a-phone"))
    }

    // endregion
}
