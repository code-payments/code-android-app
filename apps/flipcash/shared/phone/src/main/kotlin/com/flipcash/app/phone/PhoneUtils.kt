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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class PhoneUtils @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exchange: Exchange,
) {
    @Volatile
    var countryLocales: List<CountryLocale> = emptyList()

    @Volatile
    private var countryCodesMap: Map<Int, CountryLocale> = emptyMap()

    @Volatile
    var defaultCountryLocale: CountryLocale = CountryLocale.Stub

    // Lazy so libphonenumber's metadata blob is parsed on first *use* (a
    // background caller), never during construction on the main thread.
    private val phoneNumberUtil by lazy { PhoneNumberUtil.createInstance(context) }

    private val loadMutex = Mutex()

    @Volatile
    private var loaded = false

    /**
     * Loads libphonenumber metadata and builds the sorted country list. Heavy
     * (metadata parse + ~250-region enumeration; hundreds of ms on low-end
     * devices), so it MUST be called from a background dispatcher — never during
     * construction or on the main thread. Idempotent and safe to call from
     * multiple sites.
     */
    suspend fun ensureLoaded() {
        if (loaded) return
        loadMutex.withLock {
            if (loaded) return
            val locales = phoneNumberUtil.supportedRegions.map { region ->
                val countryCode = phoneNumberUtil.getCountryCodeForRegion(region)
                val resId: Int? = exchange.getFlag(region)
                val displayCountry = Locale(Locale.getDefault().language, region).displayCountry

                CountryLocale(
                    name = displayCountry,
                    phoneCode = countryCode,
                    countryCode = region,
                    resId = resId,
                )
            }
                .sortedBy { it.name }
                .filter { it.resId != null }

            countryLocales = locales
            countryCodesMap = locales.associateBy { it.phoneCode }
            val isoCountry = Locale.getDefault().country
            defaultCountryLocale =
                locales.find { it.countryCode == isoCountry }
                    ?: locales.firstOrNull()
                    ?: CountryLocale(
                        name = Locale(Locale.getDefault().language, isoCountry).displayCountry,
                        phoneCode = phoneNumberUtil.getCountryCodeForRegion(isoCountry),
                        countryCode = isoCountry,
                        resId = null,
                    )
            loaded = true
        }
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
        val countryCode = locale.countryCode
        val javaLocale = Locale(Locale.getDefault().language, countryCode)

        if (number.trimStart().startsWith("+")) {
            // Already has country code (e.g. from autofill), parse as-is
            return number.makeE164(javaLocale)
        }

        val areaCode = locale.phoneCode
        val phoneNumberCombined = areaCode.toString() + number
        return phoneNumberCombined.makeE164(javaLocale)
    }

    private fun String.makeE164(locale: Locale? = null): String {
        return try {
            val p = phoneNumberUtil.parse(this, (locale ?: Locale.getDefault()).country)
            phoneNumberUtil.format(p, PhoneNumberUtil.PhoneNumberFormat.E164)
        } catch (e: NumberParseException) {
            // Expected when the entered value isn't a valid number for the region
            // (e.g. an incomplete number mid-onboarding). Not notifiable.
            ""
        } catch (e: NumberFormatException) {
            ""
        } catch (e: Exception) {
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

    /**
     * Parse an international phone number (e.g. "+15551234567") into its country locale
     * and national number digits. Returns null if parsing fails.
     *
     * Requires [ensureLoaded] to have completed before calling — parsing uses
     * [defaultCountryLocale] as the hint region, which is empty until loaded.
     */
    fun parseInternationalNumber(number: String): Pair<CountryLocale, String>? {
        val cleaned = number.filter { it.isDigit() || it == '+' }
        if (cleaned.isBlank() || !cleaned.startsWith("+")) return null

        return try {
            val parsed = phoneNumberUtil.parse(cleaned, defaultCountryLocale.countryCode)
            if (!phoneNumberUtil.isValidNumber(parsed)) return null

            val regionCode = phoneNumberUtil.getRegionCodeForNumber(parsed) ?: return null
            val locale = countryLocales.find { it.countryCode == regionCode } ?: return null
            val nationalNumber = parsed.nationalNumber.toString()
            locale to nationalNumber
        } catch (_: NumberParseException) {
            null
        }
    }

    /**
     * Converts a raw phone number string to E.164 format. Returns null if the
     * number is blank, unparseable, invalid, or of UNKNOWN type.
     *
     * Requires [ensureLoaded] to have completed before calling — parsing uses
     * [defaultCountryLocale] as the hint region, which is empty until loaded.
     */
    fun toE164(rawNumber: String): String? {
        val cleaned = rawNumber.filter { it.isDigit() || it == '+' }
        if (cleaned.isBlank()) return null

        return try {
            val parsed = phoneNumberUtil.parse(cleaned, defaultCountryLocale.countryCode)
            if (!phoneNumberUtil.isValidNumber(parsed)) return null
            val type = phoneNumberUtil.getNumberType(parsed)
            if (type == PhoneNumberUtil.PhoneNumberType.UNKNOWN) return null
            phoneNumberUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
        } catch (_: NumberParseException) {
            null
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