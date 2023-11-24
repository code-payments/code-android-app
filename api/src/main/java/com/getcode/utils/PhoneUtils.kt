package com.getcode.utils

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.*

object PhoneUtils {
    fun makeE164(phoneValue: String, locale: Locale? = null): String {
        return try {
            val p = PhoneNumberUtil.getInstance().parse(phoneValue, (locale ?: Locale.getDefault()).country)
            PhoneNumberUtil.getInstance().format(p, PhoneNumberUtil.PhoneNumberFormat.E164)
        } catch(e: Exception) {
            ErrorUtils.handleError(e)
            ""
        }
    }
}