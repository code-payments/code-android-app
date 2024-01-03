package com.getcode.utils

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.*

fun String.makeE164(locale: Locale? = null): String {
    return try {
        val p = PhoneNumberUtil.getInstance().parse(this, (locale ?: Locale.getDefault()).country)
        PhoneNumberUtil.getInstance().format(p, PhoneNumberUtil.PhoneNumberFormat.E164)
    } catch(e: Exception) {
        ErrorUtils.handleError(e)
        ""
    }
}