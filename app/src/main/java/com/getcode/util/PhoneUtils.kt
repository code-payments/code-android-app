package com.getcode.util

import android.content.Context
import android.telephony.PhoneNumberUtils
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.getcode.App
import dagger.hilt.android.qualifiers.ApplicationContext
import io.michaelrocks.libphonenumber.android.NumberParseException
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class PhoneUtils @Inject constructor(
    @ApplicationContext private val context: Context,
    currencyUtils: CurrencyUtils
) {
    var countryLocales: List<CountryLocale> = listOf()
    private var countryCodesMap: Map<Int, CountryLocale> = mapOf()
    var defaultCountryLocale: CountryLocale

    init {
        val phoneNumberUtil = PhoneNumberUtil.createInstance(context)

        phoneNumberUtil.supportedRegions.map { region ->
            val countryCode = phoneNumberUtil.getCountryCodeForRegion(region)
            val resId: Int? = currencyUtils.getFlag(region)
            val displayCountry = Locale(Locale.getDefault().language, region).displayCountry

            CountryLocale(
                name = displayCountry,
                phoneCode = countryCode,
                countryCode = region,
                resId = resId
            )
        }
            .sortedBy { it.name }
            .let {
                countryLocales = it
            }

        countryCodesMap = countryLocales.map { it }.associateBy { it.phoneCode }
        val isoCountry = Locale.getDefault().country
        defaultCountryLocale =
            countryLocales.find { it.countryCode == isoCountry } ?: countryLocales.first()
    }

    data class CountryLocale(
        val name: String,
        val phoneCode: Int,
        val countryCode: String,
        val resId: Int? = null
    )


    fun getCountryCode(number: String): String {
        val map = countryCodesMap
        for (k in map.keys) {
            if (number.startsWith(k.toString())) return map[k]!!.countryCode
        }

        return Locale.getDefault().country
    }

    fun isPhoneNumberValid(number: String, countryCode: String): Boolean {
        val phoneNumberUtil: PhoneNumberUtil = PhoneNumberUtil.createInstance(context)
        var isValid = false
        var numberType: PhoneNumberUtil.PhoneNumberType? = null

        try {
            //val countryCallingCode: Int = phoneNumberUtil.getCountryCodeForRegion(countryCode)
            //val countryCode: String = phoneNumberUtil.getRegionCodeForCountryCode(countryCallingCode)
            val phoneNumber = phoneNumberUtil.parse(number, countryCode)
            isValid = phoneNumberUtil.isValidNumber(phoneNumber)
            numberType = phoneNumberUtil.getNumberType(phoneNumber)
        } catch (e: NumberParseException) {
            //e.printStackTrace()
        } catch (e: NullPointerException) {
            //e.printStackTrace()
        } catch (e: NumberFormatException) {
            //e.printStackTrace()
        }

        if (isValid && (PhoneNumberUtil.PhoneNumberType.UNKNOWN !== numberType)
        ) {
            return true
        }
        return false
    }

    fun formatNumber(
        number: String
    ): String {
        val countryCode = getCountryCode(number)
        return formatNumber(number, countryCode)
    }

    fun formatNumber(number: String, countryCode: String, plus: Boolean = true): String {
        val numberFormatted = (PhoneNumberUtils.formatNumber(number, countryCode) ?: number)
        return if (plus && !numberFormatted.startsWith("+")) "+$numberFormatted"
        else numberFormatted
    }

    fun toFlagEmoji(country: String): String {
        // 1. It first checks if the string consists of only 2 characters: ISO 3166-1 alpha-2 two-letter country codes (https://en.wikipedia.org/wiki/Regional_Indicator_Symbol).
        if (country.length != 2) {
            return country
        }

        val countryCodeCaps =
            country.uppercase(Locale.CANADA) // upper case is important because we are calculating offset
        val firstLetter = Character.codePointAt(countryCodeCaps, 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(countryCodeCaps, 1) - 0x41 + 0x1F1E6

        // 2. It then checks if both characters are alphabet
        if (!countryCodeCaps[0].isLetter() || !countryCodeCaps[1].isLetter()) {
            return country
        }

        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }
}