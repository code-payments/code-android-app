package com.flipcash.app.core.resources

/**
 * `Int` IDs for the `app:core` resources that common UI hands to [com.getcode.util.resources.stringRes]
 * and [com.getcode.util.resources.painterForResId]. Android returns the `R` IDs; desktop registers the
 * generated Compose resources.
 */
@Suppress("PropertyName")
expect object CoreStrings {
    val title_addMoney: Int
    val subtitle_addMoney: Int
    val title_scanTipCard: Int
    val subtitle_scanTipCard: Int
    val label_fromAppreciation: Int
    val label_fromDepreciation: Int
    val label_fromCurrencyAppreciation: Int
    val label_fromCurrencyDepreciation: Int
    val title_recentActivity: Int
    val label_chatReceipt_yesterday: Int
    val title_tipOnboarding: Int
    val action_createYourOwnCurrency: Int
    val action_currencyLeaderboard: Int
    val action_addDollars: Int
    val action_withdrawDollars: Int
}

@Suppress("PropertyName")
expect object CoreDrawables {
    val ic_nav_tipcard: Int
    val ic_checked_green: Int
    val ic_chevron_right: Int
    val ic_coins_add: Int
    val ic_globe: Int
    val ic_menu_deposit: Int
    val ic_menu_withdraw: Int
}
