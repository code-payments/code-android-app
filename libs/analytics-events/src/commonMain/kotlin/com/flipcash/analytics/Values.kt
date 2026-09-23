package com.flipcash.analytics

/** The `State` property on an event that reports an outcome. */
enum class State(val value: String) { SUCCESS("Success"), FAILURE("Failure") }

enum class AddMoneySource(val value: String) {
    MENU("Menu"), GIVE_SHORTFALL("Give Shortfall"), BUY_SHORTFALL("Buy Shortfall"),
    USERNAME_SHORTFALL("Username Shortfall"), CHAT("Chat"), SCANNER("Scanner"), BALANCE("Balance"),
}

enum class AddMoneyMethod(val value: String) {
    COINBASE("Coinbase"), PHANTOM("Phantom"), OTHER_WALLET("Other Wallet"),
    // DRIFT: iOS has no Reserves method.
    RESERVES("Reserves"),
}

enum class DisplayNameSource(val value: String) {
    ONBOARDING("Onboarding"), MY_ACCOUNT("My Account"), TIP_CARD_SETUP("Tip Card Setup"),
}

/**
 * Where Token Info was opened from. Discovery, Chat and Chat Gate are new in part 1; Android
 * previously sent Wallet for all three. iOS's Give and Send sources stay native until part 2.
 */
enum class TokenInfoSource(val value: String) {
    DEEPLINK("Deeplink"), WALLET("Wallet"), DISCOVERY("Discovery"), CHAT("Chat"), CHAT_GATE("Chat Gate"),
}

enum class PurchaseMethod(val value: String) { RESERVES("Reserves"), PHANTOM("Phantom"), COINBASE("Coinbase") }

enum class WalletProvider(val value: String) { PHANTOM("Phantom") }

enum class CashLinkChoice(val value: String) { COPIED("Copied to clipboard"), SHARED("Shared to app") }

enum class OnrampStep(val value: String) {
    SHOW_INFO("Show Verification Info"), ENTER_PHONE("Show Enter Phone"), CONFIRM_PHONE("Show Confirm Phone"),
    ENTER_EMAIL("Show Enter Email"), CONFIRM_EMAIL("Show Confirm Email"),
}

enum class Button(val value: String) {
    CREATE_ACCOUNT("Create Account"), SAVE_ACCESS_KEY("Save Access Key"), WROTE_ACCESS_KEY("Wrote Access Key"),
    ALLOW_PUSH("Allow Push"), SKIP_PUSH("Skip Push"), ALLOW_CONTACTS("Allow Contacts"), SKIP_CONTACTS("Skip Contacts"),
    BUY_WITH_RESERVES("Buy With Reserves"), BUY_WITH_PHANTOM("Buy With Phantom"), BUY_WITH_COINBASE("Buy With Coinbase"),
    BUY_WITH_OTHER_WALLET("Buy With Other Wallet"), SHARE_TOKEN_INFO("Share Token Info"),
}
