package com.flipcash.app.phone

import com.getcode.opencode.exchange.Exchange
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PhoneUtilsTest {

    private lateinit var phoneUtils: PhoneUtils
    private val mockPhoneNumberUtil = mockk<PhoneNumberUtil>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(PhoneNumberUtil::class)
        every { PhoneNumberUtil.createInstance(any<android.content.Context>()) } returns mockPhoneNumberUtil
        every { mockPhoneNumberUtil.supportedRegions } returns setOf("US", "GB", "CA", "DE")
        every { mockPhoneNumberUtil.getCountryCodeForRegion("US") } returns 1
        every { mockPhoneNumberUtil.getCountryCodeForRegion("GB") } returns 44
        every { mockPhoneNumberUtil.getCountryCodeForRegion("CA") } returns 1
        every { mockPhoneNumberUtil.getCountryCodeForRegion("DE") } returns 49

        val context = RuntimeEnvironment.getApplication()
        val exchange = mockk<Exchange>(relaxed = true) {
            every { getFlag(any()) } returns 0
        }
        phoneUtils = PhoneUtils(context, exchange)
    }

    @After
    fun tearDown() {
        unmockkStatic(PhoneNumberUtil::class)
    }

    // region toFlagEmoji

    @Test
    fun `toFlagEmoji converts US to flag emoji`() {
        val result = phoneUtils.toFlagEmoji("US")
        val expected = "\uD83C\uDDFA\uD83C\uDDF8"
        assertEquals(expected, result)
    }

    @Test
    fun `toFlagEmoji converts GB to flag emoji`() {
        val result = phoneUtils.toFlagEmoji("GB")
        val expected = "\uD83C\uDDEC\uD83C\uDDE7"
        assertEquals(expected, result)
    }

    @Test
    fun `toFlagEmoji returns input for non-2-letter string`() {
        assertEquals("USA", phoneUtils.toFlagEmoji("USA"))
        assertEquals("U", phoneUtils.toFlagEmoji("U"))
    }

    @Test
    fun `toFlagEmoji returns input for numeric string`() {
        assertEquals("12", phoneUtils.toFlagEmoji("12"))
    }

    // endregion

    // region isPhoneNumberValid

    @Test
    fun `isPhoneNumberValid accepts valid US number`() {
        val mockNumber = mockk<io.michaelrocks.libphonenumber.android.Phonenumber.PhoneNumber>(relaxed = true)
        every { mockPhoneNumberUtil.parse("+12025551234", "US") } returns mockNumber
        every { mockPhoneNumberUtil.isValidNumber(mockNumber) } returns true
        every { mockPhoneNumberUtil.getNumberType(mockNumber) } returns PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE

        assertTrue(phoneUtils.isPhoneNumberValid("+12025551234", "US"))
    }

    @Test
    fun `isPhoneNumberValid accepts valid UK number`() {
        val mockNumber = mockk<io.michaelrocks.libphonenumber.android.Phonenumber.PhoneNumber>(relaxed = true)
        every { mockPhoneNumberUtil.parse("+447911123456", "GB") } returns mockNumber
        every { mockPhoneNumberUtil.isValidNumber(mockNumber) } returns true
        every { mockPhoneNumberUtil.getNumberType(mockNumber) } returns PhoneNumberUtil.PhoneNumberType.MOBILE

        assertTrue(phoneUtils.isPhoneNumberValid("+447911123456", "GB"))
    }

    @Test
    fun `isPhoneNumberValid rejects invalid number`() {
        val mockNumber = mockk<io.michaelrocks.libphonenumber.android.Phonenumber.PhoneNumber>(relaxed = true)
        every { mockPhoneNumberUtil.parse("12345", "US") } returns mockNumber
        every { mockPhoneNumberUtil.isValidNumber(mockNumber) } returns false

        assertFalse(phoneUtils.isPhoneNumberValid("12345", "US"))
    }

    @Test
    fun `isPhoneNumberValid rejects empty string`() {
        every { mockPhoneNumberUtil.parse("", "US") } throws io.michaelrocks.libphonenumber.android.NumberParseException(
            io.michaelrocks.libphonenumber.android.NumberParseException.ErrorType.NOT_A_NUMBER,
            "empty"
        )

        assertFalse(phoneUtils.isPhoneNumberValid("", "US"))
    }

    // endregion

    // region getCountryCode

    @Test
    fun `getCountryCode detects US from prefix 1`() {
        assertEquals("US", phoneUtils.getCountryCode("12025551234"))
    }

    @Test
    fun `getCountryCode detects UK from prefix 44`() {
        assertEquals("GB", phoneUtils.getCountryCode("447911123456"))
    }

    @Test
    fun `getCountryCode returns default for unknown prefix`() {
        val result = phoneUtils.getCountryCode("99999999999")
        assertTrue(result.isNotEmpty())
    }

    // endregion

    // region toE164

    @Test
    fun `toE164 normalizes international number with plus`() {
        val mockNumber = mockk<io.michaelrocks.libphonenumber.android.Phonenumber.PhoneNumber>(relaxed = true)
        every { mockPhoneNumberUtil.parse("+12025551234", "US") } returns mockNumber
        every { mockPhoneNumberUtil.isValidNumber(mockNumber) } returns true
        every { mockPhoneNumberUtil.getNumberType(mockNumber) } returns PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE
        every { mockPhoneNumberUtil.format(mockNumber, PhoneNumberUtil.PhoneNumberFormat.E164) } returns "+12025551234"

        assertEquals("+12025551234", phoneUtils.toE164("+12025551234"))
    }

    @Test
    fun `toE164 normalizes national number without country code`() {
        val mockNumber = mockk<io.michaelrocks.libphonenumber.android.Phonenumber.PhoneNumber>(relaxed = true)
        every { mockPhoneNumberUtil.parse("2025551234", "US") } returns mockNumber
        every { mockPhoneNumberUtil.isValidNumber(mockNumber) } returns true
        every { mockPhoneNumberUtil.getNumberType(mockNumber) } returns PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE
        every { mockPhoneNumberUtil.format(mockNumber, PhoneNumberUtil.PhoneNumberFormat.E164) } returns "+12025551234"

        assertEquals("+12025551234", phoneUtils.toE164("2025551234"))
    }

    @Test
    fun `toE164 strips formatting characters`() {
        val mockNumber = mockk<io.michaelrocks.libphonenumber.android.Phonenumber.PhoneNumber>(relaxed = true)
        every { mockPhoneNumberUtil.parse("+12025551234", "US") } returns mockNumber
        every { mockPhoneNumberUtil.isValidNumber(mockNumber) } returns true
        every { mockPhoneNumberUtil.getNumberType(mockNumber) } returns PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE
        every { mockPhoneNumberUtil.format(mockNumber, PhoneNumberUtil.PhoneNumberFormat.E164) } returns "+12025551234"

        assertEquals("+12025551234", phoneUtils.toE164("+1 (202) 555-1234"))
    }

    @Test
    fun `toE164 returns null for blank input`() {
        assertNull(phoneUtils.toE164(""))
        assertNull(phoneUtils.toE164("   "))
    }

    @Test
    fun `toE164 returns null for invalid number`() {
        val mockNumber = mockk<io.michaelrocks.libphonenumber.android.Phonenumber.PhoneNumber>(relaxed = true)
        every { mockPhoneNumberUtil.parse("123", "US") } returns mockNumber
        every { mockPhoneNumberUtil.isValidNumber(mockNumber) } returns false

        assertNull(phoneUtils.toE164("123"))
    }

    @Test
    fun `toE164 returns null for unparseable number`() {
        every { mockPhoneNumberUtil.parse("abc", "US") } throws io.michaelrocks.libphonenumber.android.NumberParseException(
            io.michaelrocks.libphonenumber.android.NumberParseException.ErrorType.NOT_A_NUMBER,
            "not a number"
        )

        assertNull(phoneUtils.toE164("abc"))
    }

    @Test
    fun `toE164 returns null for UNKNOWN number type`() {
        val mockNumber = mockk<io.michaelrocks.libphonenumber.android.Phonenumber.PhoneNumber>(relaxed = true)
        every { mockPhoneNumberUtil.parse("5551234567", "US") } returns mockNumber
        every { mockPhoneNumberUtil.isValidNumber(mockNumber) } returns true
        every { mockPhoneNumberUtil.getNumberType(mockNumber) } returns PhoneNumberUtil.PhoneNumberType.UNKNOWN

        assertNull(phoneUtils.toE164("5551234567"))
    }

    // endregion

    // region formatNumber

    @Test
    fun `formatNumber adds plus prefix`() {
        val result = phoneUtils.formatNumber("12025551234", "US", plus = true)
        assertTrue(result.startsWith("+"))
    }

    @Test
    fun `formatNumber without plus prefix`() {
        // PhoneNumberUtils.formatNumber returns null for unrecognized numbers in Robolectric,
        // so the raw number is returned
        val result = phoneUtils.formatNumber("12025551234", "US", plus = false)
        assertFalse(result.startsWith("+"))
    }

    // endregion
}
