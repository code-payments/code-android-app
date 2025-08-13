package com.flipcash.app.phone.components.transformations

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.runtime.Stable
import com.flipcash.app.phone.CountryLocale
import com.flipcash.app.phone.PhoneUtils

@Stable
class PhoneNumberOutputTransformation(
    private val phoneUtils: PhoneUtils,
    private val locale: CountryLocale,
) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val originalText = asCharSequence().toString()
        if (originalText.isEmpty()) return

        val formattedNumber = phoneUtils.formatNumber(
            number = originalText,
            countryCode = locale.countryCode,
            plus = false
        )

        if (formattedNumber == originalText) return

        // Replace the entire buffer with the formatted number
        replace(start = 0, end = length, text = formattedNumber as CharSequence)
    }
}