package com.getcode.utils

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

fun String.makeE164(locale: Locale? = null): String {
    return try {
        val p = PhoneNumberUtil.getInstance().parse(this, (locale ?: Locale.getDefault()).country)
        PhoneNumberUtil.getInstance().format(p, PhoneNumberUtil.PhoneNumberFormat.E164)
    } catch(e: Exception) {
        ErrorUtils.handleError(e)
        ""
    }
}

fun String.addLeadingZero(upTo: Int): String {
    if (upTo < length) return this
    val padding = "0".repeat(length - upTo)
    return "$padding$this"
}

fun String.base64EncodedData(): ByteArray {
    val data = toByteArray()
    val r = data.size % 4
    if (r > 0) {
        val requiredPadding = data.size + 4 - r
        val padding = "=".repeat(requiredPadding)
        return data + padding.toByteArray()
    }
    return data
}