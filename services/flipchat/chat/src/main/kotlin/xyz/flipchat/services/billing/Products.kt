package xyz.flipchat.services.billing

enum class IapProduct(internal val productId: String, internal val isConsumable: Boolean) {
    CreateAccount("com.flipchat.iap.createaccount", true)
}