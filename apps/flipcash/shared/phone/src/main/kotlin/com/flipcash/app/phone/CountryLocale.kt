package com.flipcash.app.phone

data class CountryLocale(
    val name: String,
    val phoneCode: Int,
    val countryCode: String,
    val resId: Int? = null
) {
    companion object {
        val Stub = CountryLocale(name = "", phoneCode = 0, countryCode = "")
    }
}

fun CountryLocale.isStub(): Boolean = this == CountryLocale.Stub