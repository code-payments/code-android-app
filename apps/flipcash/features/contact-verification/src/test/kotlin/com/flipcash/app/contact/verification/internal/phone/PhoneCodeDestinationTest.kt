package com.flipcash.app.contact.verification.internal.phone

import com.flipcash.app.phone.CountryLocale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The onboarding code screen names the number the code went to with its country code
 * ("+1 (500) 555-0000", frame 2-04, node 13729:1502). `formattedPhone` drops the code because it
 * feeds the phone field, which shows the code beside it.
 */
class PhoneCodeDestinationTest {

    @Test
    fun `the destination carries the country code before the formatted number`() {
        val state = PhoneVerificationViewModel.State(
            formattedPhone = "(500) 555-0000",
            selectedLocale = CountryLocale(name = "United States", phoneCode = 1, countryCode = "US"),
        )

        assertEquals("+1 (500) 555-0000", state.codeDestination)
    }
}
