package com.getcode.opencode.internal.format

// A port of the ICU4J number parser that Android 16's java.text.DecimalFormat delegates to
// (android.icu.impl.number.parse.NumberParserImpl, greedy mode) in the configuration
// CompatibleDecimalFormatFactory gives it: ParseMode.JAVA_COMPATIBILITY, which is
// STRICT_SEPARATORS | USE_FULL_AFFIXES | EXACT_AFFIX | JAVA_COMPATIBILITY_IGNORABLES, case-insensitive,
// with GROUPING_DISABLED when grouping is off. Class and method names follow the ICU source so the two
// can be read side by side.
//
// Deviations from the ICU source:
//  - The Unicode sets (separators, signs, bidi controls) are ICU 76 data dumped from ICU4J, which is the
//    closest data available here to Android 16's.
//  - Locale digit strings (DecimalFormatSymbols.getDigitStringsLocal) are not used; any Unicode Nd digit
//    parses, which is what ICU does for every locale whose zero digit is itself an Nd digit.
//  - Case folding is simple (upper then lower case) instead of UCharacter.foldCase.
//  - Only the matchers this mode builds for a pattern with no affix text are ported: the negative-prefix
//    affix matcher (a minus-sign token), NaN, infinity, ignorables, decimal and scientific. Percent,
//    permille, currency, padding and plus-sign matchers are never created for these patterns.
//  - The number comes back as a Double: ICU returns a Long when the value is an integer that fits, else a
//    BigDecimal, and java.text.DecimalFormat's callers then call toDouble(); both round to the nearest
//    Double, as does parsing the decimal string, which is what is done here.

/** The locale symbols the parser reads from DecimalFormatSymbols. */
internal class IcuSymbols(
    val decimal: String,
    val grouping: String,
    val minus: String,
    val exponent: String,
    val nan: String,
    val infinity: String,
) {
    companion object {
        val US = IcuSymbols(decimal = ".", grouping = ",", minus = "-", exponent = "E", nan = "NaN", infinity = "∞")

        fun forLocale(localeTag: String?): IcuSymbols = runCatching {
            val intl = js("Intl")
            val locale: dynamic = localeTag ?: undefined
            fun parts(value: Double, options: dynamic): dynamic {
                val format = js("new intl.NumberFormat(locale, options)")
                return format.formatToParts(value)
            }
            fun find(parts: dynamic, type: String): String? {
                val length = parts.length as Int
                for (i in 0 until length) {
                    if (parts[i].type == type) return parts[i].value as String
                }
                return null
            }

            val plain = js("({})")
            val scientific = js("({notation: 'scientific'})")
            val number = parts(-1234567.5, plain)
            IcuSymbols(
                decimal = find(number, "decimal") ?: US.decimal,
                grouping = find(number, "group") ?: US.grouping,
                minus = find(number, "minusSign") ?: US.minus,
                exponent = find(parts(1e5, scientific), "exponentSeparator") ?: US.exponent,
                nan = find(parts(Double.NaN, plain), "nan") ?: US.nan,
                infinity = find(parts(Double.POSITIVE_INFINITY, plain), "infinity") ?: US.infinity,
            )
        }.getOrDefault(US)
    }
}

/** Parses [text] the way a `DecimalFormat` for [symbols] would, or returns null where ICU fails. */
internal fun icuParseDecimal(text: String, symbols: IcuSymbols, groupingUsed: Boolean): Double? {
    // android.icu.text.DecimalFormat.parse: an empty input is an empty result, not an error.
    if (text.isEmpty()) return null
    val parser = IcuNumberParser(symbols, groupingUsed)
    val result = ParsedNumber()
    parser.parse(text, result)
    return if (result.success()) result.toDouble() else null
}

// region Unicode sets

internal interface CpSet {
    operator fun contains(cp: Int): Boolean
}

private class RangeSet(private vararg val ranges: Int) : CpSet {
    override fun contains(cp: Int): Boolean {
        var i = 0
        while (i < ranges.size) {
            if (cp >= ranges[i] && cp <= ranges[i + 1]) return true
            i += 2
        }
        return false
    }
}

private class UnionSet(private vararg val sets: CpSet) : CpSet {
    override fun contains(cp: Int): Boolean = sets.any { cp in it }
}

private object DigitSet : CpSet {
    override fun contains(cp: Int): Boolean = digitValue(cp) >= 0
}

/** UnicodeSet.contains(String): true for a one-code-point string whose code point is in the set. */
private fun CpSet.containsString(s: String): Boolean {
    if (s.isEmpty()) return false
    val cp = codePointAt(s, 0, s.length)
    return charCount(cp) == s.length && cp in this
}

private object Sets {
    val EMPTY: CpSet = RangeSet()
    val STRICT_IGNORABLES: CpSet = RangeSet(0x061C, 0x061C, 0x200E, 0x200F, 0x202A, 0x202E, 0x2066, 0x2069)
    val COMMA: CpSet = RangeSet(0x002C, 0x002C, 0x060C, 0x060C, 0x066B, 0x066B, 0x2E41, 0x2E41, 0x3001, 0x3001, 0xFE10, 0xFE11, 0xFE50, 0xFE51, 0xFF0C, 0xFF0C, 0xFF64, 0xFF64)
    val PERIOD: CpSet = RangeSet(0x002E, 0x002E, 0x2024, 0x2024, 0x3002, 0x3002, 0xFE12, 0xFE12, 0xFE52, 0xFE52, 0xFF0E, 0xFF0E, 0xFF61, 0xFF61)
    val STRICT_COMMA: CpSet = RangeSet(0x002C, 0x002C, 0x066B, 0x066B, 0x2E41, 0x2E41, 0xFE10, 0xFE10, 0xFE50, 0xFE50, 0xFF0C, 0xFF0C)
    val STRICT_PERIOD: CpSet = RangeSet(0x002E, 0x002E, 0x2024, 0x2024, 0xFE52, 0xFE52, 0xFF0E, 0xFF0E, 0xFF61, 0xFF61)
    val OTHER_GROUPING_SEPARATORS: CpSet = RangeSet(
        0x0020, 0x0020, 0x0027, 0x0027, 0x00A0, 0x00A0, 0x02BC, 0x02BC, 0x055A, 0x055A, 0x066C, 0x066C, 0x1FBD, 0x1FBD,
        0x1FBF, 0x1FBF, 0x2000, 0x200A, 0x2018, 0x2019, 0x202F, 0x202F, 0x205F, 0x205F, 0x3000, 0x3000, 0xFF07, 0xFF07,
    )
    val ALL_SEPARATORS: CpSet = UnionSet(COMMA, PERIOD, OTHER_GROUPING_SEPARATORS)
    val STRICT_ALL_SEPARATORS: CpSet = UnionSet(STRICT_COMMA, STRICT_PERIOD, OTHER_GROUPING_SEPARATORS)
    val MINUS_SIGN: CpSet = RangeSet(0x002D, 0x002D, 0x2010, 0x2013, 0x207B, 0x207B, 0x208B, 0x208B, 0x2212, 0x2212, 0x2796, 0x2796, 0xFE63, 0xFE63, 0xFF0D, 0xFF0D)
    val PLUS_SIGN: CpSet = RangeSet(0x002B, 0x002B, 0x207A, 0x207A, 0x208A, 0x208A, 0x2795, 0x2795, 0xFB29, 0xFB29, 0xFE62, 0xFE62, 0xFF0B, 0xFF0B)
    val INFINITY_SIGN: CpSet = RangeSet(0x221E, 0x221E)
    val DIGITS: CpSet = DigitSet
    val DIGITS_OR_ALL_SEPARATORS: CpSet = UnionSet(DIGITS, ALL_SEPARATORS)
    val DIGITS_OR_STRICT_ALL_SEPARATORS: CpSet = UnionSet(DIGITS, STRICT_ALL_SEPARATORS)
}

// endregion

// region Code point helpers (UTF-16, as java.lang.Character)

private fun charCount(cp: Int): Int = if (cp >= 0x10000) 2 else 1

/** Character.codePointAt(seq, index) for a sequence of [limit] chars. */
private fun codePointAt(s: String, index: Int, limit: Int): Int {
    val lead = s[index]
    if (lead.isHighSurrogate() && index + 1 < limit) {
        val trail = s[index + 1]
        if (trail.isLowSurrogate()) return 0x10000 + ((lead.code - 0xD800) shl 10) + (trail.code - 0xDC00)
    }
    return lead.code
}

private fun isNdSupplementary(cp: Int): Boolean {
    val c: dynamic = js("String").fromCodePoint(cp)
    return js("/^\\p{Nd}\$/u").test(c) as Boolean
}

/** UCharacter.digit: the decimal value of a Unicode Nd code point, or -1. */
private fun digitValue(cp: Int): Int {
    if (cp < 0x10000) return cp.toChar().digitToIntOrNull() ?: -1
    if (!isNdSupplementary(cp)) return -1
    // Nd characters come in contiguous runs of ten starting at zero.
    var zero = cp
    while (isNdSupplementary(zero - 1)) zero--
    return (cp - zero) % 10
}

private fun foldCase(cp: Int): Int =
    if (cp < 0x10000) cp.toChar().uppercaseChar().lowercaseChar().code else cp

// endregion

/** android.icu.impl.StringSegment. */
internal class StringSegment(private val str: String, private val foldCase: Boolean) {
    private var start = 0
    private var end = str.length

    var offset: Int
        get() = start
        set(value) { start = value }

    fun adjustOffset(delta: Int) { start += delta }

    fun adjustOffsetByCodePoint() { start += charCount(codePoint()) }

    val length: Int get() = end - start

    fun codePoint(): Int = codePointAt(str, start, end)

    fun startsWith(cp: Int): Boolean = codePointsEqual(codePoint(), cp, foldCase)

    fun startsWith(set: CpSet): Boolean = codePoint() in set

    fun startsWith(other: String?): Boolean {
        if (other == null || other.isEmpty() || length == 0) return false
        return codePointsEqual(codePointAt(str, start, end), codePointAt(other, 0, other.length), foldCase)
    }

    fun commonPrefixLength(other: String): Int {
        var offset = 0
        while (offset < minOf(length, other.length)) {
            val cp1 = codePointAt(str, start + offset, end)
            val cp2 = codePointAt(other, offset, other.length)
            if (!codePointsEqual(cp1, cp2, foldCase)) break
            offset += charCount(cp1)
        }
        return offset
    }

    private fun codePointsEqual(cp1: Int, cp2: Int, foldCase: Boolean): Boolean {
        if (cp1 == cp2) return true
        if (!foldCase) return false
        return foldCase(cp1) == foldCase(cp2)
    }
}

/** android.icu.impl.number.DecimalQuantity, reduced to value = digits x 10^scale. */
internal class DecimalQuantity {
    private var digits = ""
    private var scale = 0L
    private var origDelta = 0L
    var isNegative = false
        private set

    val isZeroish: Boolean get() = digits.isEmpty()

    fun createCopy(): DecimalQuantity {
        val copy = DecimalQuantity()
        copy.digits = digits
        copy.scale = scale
        copy.origDelta = origDelta
        copy.isNegative = isNegative
        return copy
    }

    /** appendDigit(digit, 0, appendAsInteger = true); a leading zero adds nothing. */
    fun appendDigit(digit: Int) {
        if (digit == 0 && digits.isEmpty()) return
        digits += digit.toString()
    }

    /** Throws ArithmeticException where ICU's Math.addExact on the int scale overflows. */
    fun adjustMagnitude(delta: Long) {
        if (digits.isEmpty()) return
        val newScale = scale + delta
        val newOrig = origDelta + delta
        if (newScale !in Int.MIN_VALUE..Int.MAX_VALUE || newOrig !in Int.MIN_VALUE..Int.MAX_VALUE) {
            throw ArithmeticException("integer overflow")
        }
        scale = newScale
        origDelta = newOrig
    }

    /** Drops the digits below the decimal point. */
    fun truncate() {
        if (scale < 0) {
            val drop = -scale
            digits = if (drop >= digits.length) "" else digits.substring(0, digits.length - drop.toInt())
            scale = 0
        }
    }

    fun clear() {
        digits = ""
        scale = 0
        origDelta = 0
        isNegative = false
    }

    fun negate() { isNegative = !isNegative }

    /** The integer value if it has at most 18 digits (anything longer exceeds Int.MAX_VALUE anyway), else null. */
    fun toNonNegativeLongOrNull(): Long? {
        if (scale != 0L || digits.length > 18) return null
        return if (digits.isEmpty()) 0L else digits.toLong()
    }

    /** The nearest Double, which is what both Long.toDouble() and BigDecimal.doubleValue() give. */
    fun toDouble(): Double {
        if (digits.isEmpty()) return if (isNegative) -0.0 else 0.0
        val magnitude = (digits + "e" + scale).toDouble()
        return if (isNegative) -magnitude else magnitude
    }
}

/** android.icu.impl.number.parse.ParsedNumber. */
internal class ParsedNumber {
    var quantity: DecimalQuantity? = null
    var charEnd = 0
    var flags = 0
    var prefix: String? = null
    var suffix: String? = null

    fun copyFrom(other: ParsedNumber) {
        quantity = other.quantity?.createCopy()
        charEnd = other.charEnd
        flags = other.flags
        prefix = other.prefix
        suffix = other.suffix
    }

    fun setCharsConsumed(segment: StringSegment) { charEnd = segment.offset }

    fun postProcess() {
        val q = quantity
        if (q != null && flags and FLAG_NEGATIVE != 0) q.negate()
    }

    fun success(): Boolean = charEnd > 0 && flags and FLAG_FAIL == 0

    fun seenNumber(): Boolean = quantity != null || flags and FLAG_NAN != 0 || flags and FLAG_INFINITY != 0

    fun toDouble(): Double {
        if (flags and FLAG_NAN != 0) return Double.NaN
        if (flags and FLAG_INFINITY != 0) {
            return if (flags and FLAG_NEGATIVE != 0) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
        }
        return quantity!!.toDouble()
    }

    companion object {
        const val FLAG_NEGATIVE = 0x0001
        const val FLAG_HAS_EXPONENT = 0x0008
        const val FLAG_HAS_DECIMAL_SEPARATOR = 0x0020
        const val FLAG_NAN = 0x0040
        const val FLAG_INFINITY = 0x0080
        const val FLAG_FAIL = 0x0100
    }
}

// region Matchers

internal interface NumberParseMatcher {
    fun match(segment: StringSegment, result: ParsedNumber): Boolean
    fun smokeTest(segment: StringSegment): Boolean
    fun postProcess(result: ParsedNumber) {}
}

/** Marker for matchers the series matcher may skip or repeat (NumberParseMatcher.Flexible). */
internal interface Flexible

internal abstract class SymbolMatcher(private val string: String, protected val uniSet: CpSet) : NumberParseMatcher {
    override fun match(segment: StringSegment, result: ParsedNumber): Boolean {
        if (isDisabled(result)) return false
        var overlap = 0
        if (string.isNotEmpty()) {
            overlap = segment.commonPrefixLength(string)
            if (overlap == string.length) {
                segment.adjustOffset(string.length)
                accept(segment, result)
                return false
            }
        }
        if (segment.startsWith(uniSet)) {
            segment.adjustOffsetByCodePoint()
            accept(segment, result)
            return false
        }
        return overlap == segment.length
    }

    override fun smokeTest(segment: StringSegment): Boolean = segment.startsWith(uniSet) || segment.startsWith(string)

    protected abstract fun isDisabled(result: ParsedNumber): Boolean
    protected abstract fun accept(segment: StringSegment, result: ParsedNumber)
}

internal class IgnorablesMatcher(set: CpSet) : SymbolMatcher("", set), Flexible {
    override fun isDisabled(result: ParsedNumber) = false
    override fun accept(segment: StringSegment, result: ParsedNumber) {}
}

internal class NanMatcher(symbol: String) : SymbolMatcher(symbol, Sets.EMPTY) {
    override fun isDisabled(result: ParsedNumber) = result.seenNumber()
    override fun accept(segment: StringSegment, result: ParsedNumber) {
        result.flags = result.flags or ParsedNumber.FLAG_NAN
        result.setCharsConsumed(segment)
    }
}

internal class InfinityMatcher(symbol: String) :
    SymbolMatcher(if (Sets.INFINITY_SIGN.containsString(symbol)) "" else symbol, Sets.INFINITY_SIGN) {
    override fun isDisabled(result: ParsedNumber) = result.flags and ParsedNumber.FLAG_INFINITY != 0
    override fun accept(segment: StringSegment, result: ParsedNumber) {
        result.flags = result.flags or ParsedNumber.FLAG_INFINITY
        result.setCharsConsumed(segment)
    }
}

internal class MinusSignMatcher(symbol: String, private val allowTrailing: Boolean) :
    SymbolMatcher(if (Sets.MINUS_SIGN.containsString(symbol)) "" else symbol, Sets.MINUS_SIGN) {
    override fun isDisabled(result: ParsedNumber) = !allowTrailing && result.seenNumber()
    override fun accept(segment: StringSegment, result: ParsedNumber) {
        result.flags = result.flags or ParsedNumber.FLAG_NEGATIVE
        result.setCharsConsumed(segment)
    }
}

/** SeriesMatcher; with [pattern] set it is an AffixPatternMatcher. */
internal class SeriesMatcher(val pattern: String, private val matchers: List<NumberParseMatcher>) : NumberParseMatcher {
    override fun match(segment: StringSegment, result: ParsedNumber): Boolean {
        val backup = ParsedNumber()
        backup.copyFrom(result)
        val initialOffset = segment.offset
        var maybeMore = true
        var i = 0
        while (i < matchers.size) {
            val matcher = matchers[i]
            val matcherOffset = segment.offset
            maybeMore = if (segment.length != 0) matcher.match(segment, result) else true
            val success = segment.offset != matcherOffset
            val isFlexible = matcher is Flexible
            if (success && isFlexible) {
                // Flexible matchers may match again.
            } else if (success) {
                i++
                if (i < matchers.size && segment.offset != result.charEnd && result.charEnd > matcherOffset) {
                    // Some matchers consume less than their segment; resume where they stopped.
                    segment.offset = result.charEnd
                }
            } else if (isFlexible) {
                i++
            } else {
                segment.offset = initialOffset
                result.copyFrom(backup)
                return maybeMore
            }
        }
        return maybeMore
    }

    override fun smokeTest(segment: StringSegment): Boolean = matchers[0].smokeTest(segment)

    override fun postProcess(result: ParsedNumber) {
        for (matcher in matchers) matcher.postProcess(result)
    }
}

internal class AffixMatcher(
    private val prefix: SeriesMatcher?,
    private val suffix: SeriesMatcher?,
    private val flags: Int,
) : NumberParseMatcher {
    override fun match(segment: StringSegment, result: ParsedNumber): Boolean {
        if (!result.seenNumber()) {
            if (result.prefix != null || prefix == null) return false
            val initialOffset = segment.offset
            val maybeMore = prefix.match(segment, result)
            if (initialOffset != segment.offset) result.prefix = prefix.pattern
            return maybeMore
        } else {
            if (result.suffix != null || suffix == null || !matched(prefix, result.prefix)) return false
            val initialOffset = segment.offset
            val maybeMore = suffix.match(segment, result)
            if (initialOffset != segment.offset) result.suffix = suffix.pattern
            return maybeMore
        }
    }

    override fun smokeTest(segment: StringSegment): Boolean =
        (prefix != null && prefix.smokeTest(segment)) || (suffix != null && suffix.smokeTest(segment))

    override fun postProcess(result: ParsedNumber) {
        if (matched(prefix, result.prefix) && matched(suffix, result.suffix)) {
            if (result.prefix == null) result.prefix = ""
            if (result.suffix == null) result.suffix = ""
            result.flags = result.flags or flags
            prefix?.postProcess(result)
            suffix?.postProcess(result)
        }
    }

    private fun matched(affix: SeriesMatcher?, patternString: String?): Boolean =
        (affix == null && patternString == null) || (affix != null && affix.pattern == patternString)
}

internal class DecimalMatcher(
    symbols: IcuSymbols,
    strictSeparators: Boolean,
    private val groupingDisabled: Boolean,
    private val integerOnly: Boolean,
    private val requireGroupingMatch: Boolean,
    private val grouping1: Int,
    private val grouping2: Int,
) : NumberParseMatcher {
    private val groupingSeparator = symbols.grouping
    private val decimalSeparator = symbols.decimal
    private val groupingUniSet: CpSet
    private val decimalUniSet: CpSet
    private val separatorSet: CpSet
    private val leadSet: CpSet?

    init {
        groupingUniSet = if (strictSeparators) Sets.STRICT_ALL_SEPARATORS else Sets.ALL_SEPARATORS
        val comma = if (strictSeparators) Sets.STRICT_COMMA else Sets.COMMA
        val period = if (strictSeparators) Sets.STRICT_PERIOD else Sets.PERIOD
        val decimalKey: CpSet? = when {
            comma.containsString(decimalSeparator) -> comma
            period.containsString(decimalSeparator) -> period
            else -> null
        }
        decimalUniSet = when {
            decimalKey != null -> decimalKey
            decimalSeparator.isNotEmpty() -> codePointAt(decimalSeparator, 0, decimalSeparator.length).let { RangeSet(it, it) }
            else -> Sets.EMPTY
        }
        if (decimalKey != null) {
            separatorSet = groupingUniSet
            // ICU picks the opposite set here (DIGITS_OR_ALL_SEPARATORS when strict); ported as written.
            leadSet = if (strictSeparators) Sets.DIGITS_OR_ALL_SEPARATORS else Sets.DIGITS_OR_STRICT_ALL_SEPARATORS
        } else {
            separatorSet = UnionSet(groupingUniSet, decimalUniSet)
            leadSet = null
        }
    }

    override fun match(segment: StringSegment, result: ParsedNumber): Boolean = match(segment, result, 0)

    fun match(segment: StringSegment, result: ParsedNumber, exponentSign: Int): Boolean {
        if (result.seenNumber() && exponentSign == 0) return false

        val initialOffset = segment.offset
        var maybeMore = false
        var digitsConsumed: DecimalQuantity? = null
        var digitsAfterDecimalPlace = 0
        var actualGroupingString: String? = null
        var actualDecimalString: String? = null
        var currGroupOffset = 0
        var currGroupSepType = 0
        var currGroupCount = 0
        var prevGroupOffset = -1
        var prevGroupSepType = -1
        var prevGroupCount = -1

        while (segment.length > 0) {
            maybeMore = false

            // Attempt to match a digit.
            var digit = -1
            val cp = segment.codePoint()
            val value = digitValue(cp)
            if (value >= 0) {
                segment.adjustOffset(charCount(cp))
                digit = value
            }

            if (digit >= 0) {
                if (digitsConsumed == null) digitsConsumed = DecimalQuantity()
                digitsConsumed.appendDigit(digit)
                currGroupCount++
                if (actualDecimalString != null) digitsAfterDecimalPlace++
                continue
            }

            // Attempt to match a literal grouping or decimal separator.
            var isDecimal = false
            var isGrouping = false

            if (actualDecimalString == null && decimalSeparator.isNotEmpty()) {
                val overlap = segment.commonPrefixLength(decimalSeparator)
                maybeMore = maybeMore || overlap == segment.length
                if (overlap == decimalSeparator.length) {
                    isDecimal = true
                    actualDecimalString = decimalSeparator
                }
            }

            if (actualGroupingString != null) {
                val overlap = segment.commonPrefixLength(actualGroupingString)
                maybeMore = maybeMore || overlap == segment.length
                if (overlap == actualGroupingString.length) isGrouping = true
            }

            if (!groupingDisabled && actualGroupingString == null && actualDecimalString == null && groupingSeparator.isNotEmpty()) {
                val overlap = segment.commonPrefixLength(groupingSeparator)
                maybeMore = maybeMore || overlap == segment.length
                if (overlap == groupingSeparator.length) {
                    isGrouping = true
                    actualGroupingString = groupingSeparator
                }
            }

            // Attempt to match a code point from an equivalent separator set.
            if (!isGrouping && actualDecimalString == null) {
                if (cp in decimalUniSet) {
                    isDecimal = true
                    actualDecimalString = codePointString(cp)
                }
            }
            if (!groupingDisabled && actualGroupingString == null && actualDecimalString == null) {
                if (cp in groupingUniSet) {
                    isGrouping = true
                    actualGroupingString = codePointString(cp)
                }
            }

            // Leave if we failed to match this as a separator.
            if (!isDecimal && !isGrouping) break

            // Check for conditions when we don't want to accept the separator.
            if (isDecimal && integerOnly) {
                break
            } else if (currGroupSepType == 2 && isGrouping) {
                // Don't accept a grouping separator after a decimal separator.
                break
            }

            // Validate intermediate grouping sizes.
            val prevValidSecondary = validateGroup(prevGroupSepType, prevGroupCount, false)
            val currValidPrimary = validateGroup(currGroupSepType, currGroupCount, true)
            if (!prevValidSecondary || (isDecimal && !currValidPrimary)) {
                // Invalid grouping sizes.
                if (isGrouping && currGroupCount == 0) {
                    // Trailing grouping separators: these are taken care of below.
                } else if (requireGroupingMatch) {
                    // Strict mode: reject the parse.
                    digitsConsumed = null
                }
                break
            } else if (requireGroupingMatch && currGroupCount == 0 && currGroupSepType == 1) {
                break
            } else {
                // Grouping sizes are valid.
                prevGroupOffset = currGroupOffset
                prevGroupCount = currGroupCount
                prevGroupSepType = if (isDecimal) -1 else currGroupSepType
            }

            // Update state variables.
            if (currGroupCount != 0) currGroupOffset = segment.offset
            currGroupSepType = if (isGrouping) 1 else 2
            currGroupCount = 0
            if (isGrouping) {
                segment.adjustOffset(actualGroupingString!!.length)
            } else {
                segment.adjustOffset(actualDecimalString!!.length)
            }
        }

        // Back up if there was a trailing grouping separator.
        if (currGroupSepType != 2 && currGroupCount == 0) {
            maybeMore = true
            segment.offset = currGroupOffset
            currGroupOffset = prevGroupOffset
            currGroupSepType = prevGroupSepType
            currGroupCount = prevGroupCount
            prevGroupOffset = -1
            prevGroupSepType = 0
            prevGroupCount = 1
        }

        // Validate final grouping sizes.
        var prevValidSecondary = validateGroup(prevGroupSepType, prevGroupCount, false)
        var currValidPrimary = validateGroup(currGroupSepType, currGroupCount, true)

        if (!requireGroupingMatch) {
            // The cases we need to handle here are listed in ICU's source: the lenient fallbacks.
            var digitsToRemove = 0
            if (!prevValidSecondary) {
                // Strip an invalid secondary group from the end of the string.
                segment.offset = prevGroupOffset
                digitsToRemove += prevGroupCount
                digitsToRemove += currGroupCount
            } else if (!currValidPrimary && (prevGroupSepType != 0 || prevGroupCount != 0)) {
                // Strip an invalid primary group from the end of the string.
                maybeMore = true
                segment.offset = currGroupOffset
                digitsToRemove += currGroupCount
            }
            if (digitsToRemove != 0) {
                digitsConsumed!!.adjustMagnitude(-digitsToRemove.toLong())
                digitsConsumed.truncate()
            }
            prevValidSecondary = true
            currValidPrimary = true
        }
        if (currGroupSepType != 2 && (!prevValidSecondary || !currValidPrimary)) {
            // Grouping failure.
            digitsConsumed = null
        }

        // Strings that start with a separator but have no digits.
        if (digitsConsumed == null) {
            maybeMore = maybeMore || segment.length == 0
            segment.offset = initialOffset
            return maybeMore
        }

        // We passed all inspections. Start post-processing.
        digitsConsumed.adjustMagnitude(-digitsAfterDecimalPlace.toLong())
        if (exponentSign != 0 && segment.offset != initialOffset) {
            var overflow = false
            val exponent = digitsConsumed.toExponentOrNull()
            if (exponent != null) {
                try {
                    result.quantity!!.adjustMagnitude(exponentSign * exponent)
                } catch (e: ArithmeticException) {
                    overflow = true
                }
            } else {
                overflow = true
            }
            if (overflow) {
                if (exponentSign == -1) {
                    result.quantity!!.clear()
                } else {
                    result.quantity = null
                    result.flags = result.flags or ParsedNumber.FLAG_INFINITY
                }
            }
        } else {
            result.quantity = digitsConsumed
        }

        if (actualDecimalString != null) result.flags = result.flags or ParsedNumber.FLAG_HAS_DECIMAL_SEPARATOR
        result.setCharsConsumed(segment)
        return segment.length == 0 || maybeMore
    }

    private fun validateGroup(sepType: Int, count: Int, isPrimary: Boolean): Boolean {
        if (requireGroupingMatch) {
            return when (sepType) {
                -1 -> true
                0 -> if (isPrimary) true else count != 0 && count <= grouping2
                1 -> if (isPrimary) count == grouping1 else count == grouping2
                else -> true
            }
        } else {
            return if (sepType == 1) count != 1 else true
        }
    }

    override fun smokeTest(segment: StringSegment): Boolean {
        // digitStrings is always null here, so only the lead set applies when there is one.
        if (leadSet != null) return segment.startsWith(leadSet)
        return segment.startsWith(separatorSet) || digitValue(segment.codePoint()) >= 0
    }

    /** The exponent value (ICU: fitsInLong, then <= Integer.MAX_VALUE), else null. */
    private fun DecimalQuantity.toExponentOrNull(): Long? = toNonNegativeLongOrNull()?.takeIf { it <= Int.MAX_VALUE }
}

private fun codePointString(cp: Int): String =
    if (cp < 0x10000) cp.toChar().toString() else {
        val v = cp - 0x10000
        charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString()
    }

internal class ScientificMatcher(private val symbols: IcuSymbols, grouper: Pair<Int, Int>) : NumberParseMatcher {
    private val exponentSeparatorString = symbols.exponent
    private val exponentMatcher = DecimalMatcher(
        symbols,
        strictSeparators = false,
        groupingDisabled = true,
        integerOnly = true,
        requireGroupingMatch = false,
        grouping1 = grouper.first,
        grouping2 = grouper.second,
    )
    private val ignorablesMatcher = IgnorablesMatcher(Sets.STRICT_IGNORABLES)
    private val customMinusSign: String? = symbols.minus.takeUnless { Sets.MINUS_SIGN.containsString(it) }
    // DecimalFormatSymbols' plus sign is "+" for every locale this runs with, which is in the plus set.
    private val customPlusSign: String? = null

    override fun match(segment: StringSegment, result: ParsedNumber): Boolean {
        // Only accept scientific notation after the mantissa.
        if (!result.seenNumber()) return false
        // Only accept one exponent per string.
        if (result.flags and ParsedNumber.FLAG_HAS_EXPONENT != 0) return false

        val initialOffset = segment.offset
        var overlap = segment.commonPrefixLength(exponentSeparatorString)
        if (overlap == exponentSeparatorString.length) {
            // Full exponent separator match. First attempt to match the sign.
            if (segment.length == overlap) return true
            segment.adjustOffset(overlap)
            ignorablesMatcher.match(segment, ParsedNumber())
            if (segment.length == 0) {
                segment.offset = initialOffset
                return true
            }

            var exponentSign = 1
            if (segment.startsWith(Sets.MINUS_SIGN)) {
                exponentSign = -1
                segment.adjustOffsetByCodePoint()
            } else if (segment.startsWith(Sets.PLUS_SIGN)) {
                segment.adjustOffsetByCodePoint()
            } else if (segment.startsWith(customMinusSign)) {
                overlap = segment.commonPrefixLength(customMinusSign!!)
                if (overlap != customMinusSign.length) {
                    // Partial custom sign match.
                    segment.offset = initialOffset
                    return true
                }
                exponentSign = -1
                segment.adjustOffset(overlap)
            } else if (segment.startsWith(customPlusSign)) {
                overlap = segment.commonPrefixLength(customPlusSign!!)
                if (overlap != customPlusSign.length) {
                    segment.offset = initialOffset
                    return true
                }
                segment.adjustOffset(overlap)
            }

            // Return true if the segment is empty after consuming the sign.
            if (segment.length == 0) {
                segment.offset = initialOffset
                return true
            }
            ignorablesMatcher.match(segment, ParsedNumber())
            if (segment.length == 0) {
                segment.offset = initialOffset
                return true
            }

            // Allow a quantity to be null: a NaN or infinity mantissa has none.
            val wasNull = result.quantity == null
            if (wasNull) result.quantity = DecimalQuantity()
            val digitsOffset = segment.offset
            val digitsReturnValue = exponentMatcher.match(segment, result, exponentSign)
            if (wasNull) result.quantity = null
            if (segment.offset != digitsOffset) {
                // At least one exponent digit was matched.
                result.flags = result.flags or ParsedNumber.FLAG_HAS_EXPONENT
            } else {
                // No exponent digits were matched; un-match the exponent separator.
                segment.offset = initialOffset
            }
            return digitsReturnValue
        } else if (overlap == segment.length) {
            // Partial exponent separator match.
            return true
        }
        return false
    }

    override fun smokeTest(segment: StringSegment): Boolean = segment.startsWith(exponentSeparatorString)
}

internal class RequireNumberValidator : NumberParseMatcher {
    override fun match(segment: StringSegment, result: ParsedNumber) = false
    override fun smokeTest(segment: StringSegment) = false
    override fun postProcess(result: ParsedNumber) {
        if (!result.seenNumber()) result.flags = result.flags or ParsedNumber.FLAG_FAIL
    }
}

internal class RequireAffixValidator : NumberParseMatcher {
    override fun match(segment: StringSegment, result: ParsedNumber) = false
    override fun smokeTest(segment: StringSegment) = false
    override fun postProcess(result: ParsedNumber) {
        if (result.prefix == null || result.suffix == null) result.flags = result.flags or ParsedNumber.FLAG_FAIL
    }
}

// endregion

/**
 * NumberParserImpl.createParserFromProperties for a plain `#,##0.###` pattern (no affix text) in
 * JAVA_COMPATIBILITY mode, and its greedy parse.
 */
internal class IcuNumberParser(symbols: IcuSymbols, groupingUsed: Boolean) {
    private val matchers: List<NumberParseMatcher>

    init {
        // Grouper.forProperties: primary 3 / secondary 3 when grouping is on (only used by the
        // strict-grouping-size checks, which this mode does not enable), primary -1 when it is off.
        val grouper = if (groupingUsed) 3 to 3 else -1 to -1
        val groupingDisabled = grouper.first <= 0

        val list = ArrayList<NumberParseMatcher>()

        // AffixMatcher.createMatchers: the positive pattern has no prefix or suffix, the negative one is
        // a minus-sign prefix. Sorted by prefix length, descending, so the negative matcher is first.
        val negativePrefix = SeriesMatcher("-", listOf(MinusSignMatcher(symbols.minus, allowTrailing = true)))
        list += AffixMatcher(negativePrefix, null, ParsedNumber.FLAG_NEGATIVE)
        list += AffixMatcher(null, null, 0)

        list += NanMatcher(symbols.nan)
        list += InfinityMatcher(symbols.infinity)
        // IgnorablesMatcher.JAVA_COMPATIBILITY: an empty set.
        list += IgnorablesMatcher(Sets.EMPTY)
        list += DecimalMatcher(
            symbols,
            strictSeparators = true,
            groupingDisabled = groupingDisabled,
            integerOnly = false,
            requireGroupingMatch = false,
            grouping1 = grouper.first,
            grouping2 = grouper.second,
        )
        list += ScientificMatcher(symbols, grouper)

        list += RequireNumberValidator()
        list += RequireAffixValidator()
        matchers = list
    }

    /** NumberParserImpl.parse(input, greedy = true, result). */
    fun parse(input: String, result: ParsedNumber) {
        val segment = StringSegment(input, foldCase = true)
        parseGreedy(segment, result)
        for (matcher in matchers) matcher.postProcess(result)
        result.postProcess()
    }

    private fun parseGreedy(segment: StringSegment, result: ParsedNumber) {
        var i = 0
        while (i < matchers.size) {
            // Base case.
            if (segment.length == 0) return
            val matcher = matchers[i]
            if (!matcher.smokeTest(segment)) {
                i++
                continue
            }
            val initialOffset = segment.offset
            matcher.match(segment, result)
            if (segment.offset != initialOffset) {
                // Greedy heuristic: accept the match and loop back.
                i = 0
            } else {
                i++
            }
        }
    }
}
