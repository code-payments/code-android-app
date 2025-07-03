package com.flipcash.app.billing

enum class IapProduct(internal val productId: String, internal val isConsumable: Boolean) {
    CreateAccount("com.flipcash.iap.createaccount", true),
    @Deprecated("Welcome bonus has been phased out for now")
    CreateAccountWithWelcomeBonus("com.flipcash.iap.createaccountwithwelcomebonus", false)
}