package com.flipcash.analytics

enum class DisplayNameSource(val value: String) {
    ONBOARDING("Onboarding"), MY_ACCOUNT("My Account"), TIP_CARD_SETUP("Tip Card Setup"),
}

enum class Button(val value: String) {
    CREATE_ACCOUNT("Create Account"), SAVE_ACCESS_KEY("Save Access Key"), WROTE_ACCESS_KEY("Wrote Access Key"),
    ALLOW_PUSH("Allow Push"), SKIP_PUSH("Skip Push"), ALLOW_CONTACTS("Allow Contacts"), SKIP_CONTACTS("Skip Contacts"),
    BUY_WITH_RESERVES("Buy With Reserves"), BUY_WITH_PHANTOM("Buy With Phantom"), BUY_WITH_COINBASE("Buy With Coinbase"),
    BUY_WITH_OTHER_WALLET("Buy With Other Wallet"), SHARE_TOKEN_INFO("Share Token Info"),
}
