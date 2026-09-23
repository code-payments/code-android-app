package com.flipcash.analytics.events

import com.flipcash.analytics.event

object AccountEvents {
    // Android only; part 2 decides whether iOS sends it. iOS's Create Account fires at
    // registration and is a different moment.
    fun createAccountPayment(price: Double, currency: String, owner: String) = event("Create Account Payment") {
        number("Fiat", price)
        text("Currency", currency)
        text("Owner Public Key", owner)
    }

    fun enteredPhoneNumber() = event("Entered Phone Number")

    fun verifiedPhoneNumber() = event("Verified Phone Number")

    fun linkedPhoneNumber() = event("Linked Phone Number")

    fun completeOnboarding() = event("Complete Onboarding")
}
