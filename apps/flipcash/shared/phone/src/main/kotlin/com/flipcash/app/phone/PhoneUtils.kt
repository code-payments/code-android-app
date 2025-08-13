package com.flipcash.app.phone

import android.content.Context
import android.telephony.PhoneNumberUtils
import com.getcode.opencode.exchange.Exchange
import com.getcode.utils.ErrorUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import io.michaelrocks.libphonenumber.android.NumberParseException
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneUtils @Inject constructor(
    @ApplicationContext private val context: Context,
    exchange: Exchange,
) {
    var countryLocales: List<CountryLocale> = listOf()
    private var countryCodesMap: Map<Int, CountryLocale> = mapOf()
    var defaultCountryLocale: CountryLocale

    private val phoneNumberUtil = PhoneNumberUtil.createInstance(context)

    init {
        countryLocales = phoneNumberUtil.supportedRegions.map { region ->
            val countryCode = phoneNumberUtil.getCountryCodeForRegion(region)
            val resId: Int? = exchange.getFlag(region)
            val displayCountry = Locale(Locale.getDefault().language, region).displayCountry

            CountryLocale(
                name = displayCountry,
                phoneCode = countryCode,
                countryCode = region,
                resId = resId
            )
        }
            .sortedBy { it.name }
            .filter { it.resId != null }

        countryCodesMap = countryLocales.map { it }.associateBy { it.phoneCode }
        val isoCountry = Locale.getDefault().country
        defaultCountryLocale =
            countryLocales.find { it.countryCode == isoCountry } ?: countryLocales.first()
    }


    fun getCountryCode(number: String): String {
        val map = countryCodesMap
        for (k in map.keys) {
            if (number.startsWith(k.toString())) return map[k]!!.countryCode
        }

        return Locale.getDefault().country
    }

    fun isPhoneNumberValid(
        number: String
    ): Boolean {
        val countryCode = getCountryCode(number)
        return isPhoneNumberValid(number, countryCode)
    }

    fun isPhoneNumberValid(number: String, countryCode: String): Boolean {
        var isValid = false
        var numberType: PhoneNumberUtil.PhoneNumberType? = null

        try {
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

        return isValid && (PhoneNumberUtil.PhoneNumberType.UNKNOWN !== numberType)
    }

    fun cleanNumber(number: String, locale: CountryLocale): String {
        val areaCode = locale.phoneCode
        val countryCode =locale.countryCode
        val phoneInput = number

        val phoneNumberCombined = areaCode.toString() + phoneInput

        val phoneNumber = phoneNumberCombined.makeE164(
            Locale(Locale.getDefault().language, countryCode)
        )

        return phoneNumber
    }

    private fun String.makeE164(locale: Locale? = null): String {
        return try {
            val p = phoneNumberUtil.parse(this, (locale ?: Locale.getDefault()).country)
            phoneNumberUtil.format(p, PhoneNumberUtil.PhoneNumberFormat.E164)
        } catch(e: Exception) {
            ErrorUtils.handleError(e)
            ""
        }
    }


    fun formatNumber(
        number: String,
        plus: Boolean = true
    ): String {
        val countryCode = getCountryCode(number)
        return formatNumber(number, countryCode, plus)
    }

    fun formatNumber(number: String, countryCode: String, plus: Boolean = true): String {
        val numberFormatted = (PhoneNumberUtils.formatNumber(number, countryCode) ?: number)
        return if (plus && !numberFormatted.startsWith("+")) {
            "+$numberFormatted"
        } else {
            numberFormatted
        }
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