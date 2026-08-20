package com.flipcash.app.onramp

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.michaelrocks.libphonenumber.android.NumberParseException
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Region derived from a phone number for Coinbase buy-options lookups.
 *
 * @property country ISO 3166-1 alpha-2 country code (e.g., "US", "CA", "GB")
 * @property subdivision ISO 3166-2 subdivision code, if detectable (e.g., "NY")
 */
data class PhoneRegion(
    val country: String,
    val subdivision: String? = null,
) {
    val cacheKey: String get() = "$country${subdivision?.let { "-$it" } ?: ""}"
}

/**
 * NYC area codes used to infer a New York subdivision.
 * Only needed for US numbers where Coinbase has state-level restrictions.
 */
private val NYC_AREA_CODES = setOf("212", "718", "917", "646", "347", "929", "586")

/**
 * Resolves the [PhoneRegion] for a phone number.
 *
 * Injectable rather than a top-level function because the Android libphonenumber port loads its
 * metadata from the AAR's assets, so it needs a [Context] — unlike Google's desktop artifact, which
 * exposes a static instance. See the note in `gradle/libs.versions.toml` for why the port is the
 * only libphonenumber this app depends on.
 */
@Singleton
class PhoneRegionResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Lazy so libphonenumber's metadata blob is parsed on first *use* (a background caller), never
    // during construction on the main thread. Same reasoning as PhoneUtils in :shared:phone.
    private val phoneUtil by lazy { PhoneNumberUtil.createInstance(context) }

    /**
     * Extracts the [PhoneRegion] from an E.164 phone number using libphonenumber
     * for country detection and area code heuristics for US subdivision.
     *
     * @return the detected region, or null if the number can't be parsed.
     */
    fun regionFromPhone(phone: String): PhoneRegion? {
        val parsed = try {
            phoneUtil.parse(phone, null)
        } catch (_: NumberParseException) {
            return null
        }

        val country = phoneUtil.getRegionCodeForNumber(parsed) ?: return null
        val subdivision = if (country == "US") {
            subdivisionFromUsNumber(parsed.nationalNumber.toString())
        } else {
            null
        }

        return PhoneRegion(country = country, subdivision = subdivision)
    }

    private fun subdivisionFromUsNumber(nationalNumber: String): String? {
        if (nationalNumber.length < 3) return null
        val areaCode = nationalNumber.take(3)
        return if (areaCode in NYC_AREA_CODES) "NY" else null
    }
}
