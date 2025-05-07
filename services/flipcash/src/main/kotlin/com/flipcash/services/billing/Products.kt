package com.flipcash.services.billing

enum class IapProduct(internal val productId: String, internal val isConsumable: Boolean) {
    CreateAccount("com.flipcash.iap.createaccount", true),
    CreateAccountWithWelcomeBonus("com.flipcash.iap.createaccountwithwelcomebonus", false)
}