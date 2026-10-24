package com.flipcash.app.userflags

/**
 * String resource ids for the debug flag editor's labels and hints. Android reads them from
 * this module's `R`; desktop has no flag editor, so its ids are 0.
 */
internal expect object FlagStrings {
    val hint_flag_billExchangeDataTimeout: Int
    val hint_flag_messageDeleteWindow: Int
    val hint_flag_messageEditWindow: Int
    val hint_flag_minHolderAmount: Int
    val hint_flag_minimumVersion: Int
    val hint_flag_newCurrencyFeeAmount: Int
    val hint_flag_newCurrencyPurchaseAmount: Int
    val hint_flag_withdrawalFeeAmount: Int
    val label_flag_billExchangeDataTimeout: Int
    val label_flag_messageDeleteWindow: Int
    val label_flag_messageEditWindow: Int
    val label_flag_minHolderAmount: Int
    val label_flag_minimumVersion: Int
    val label_flag_newCurrencyFeeAmount: Int
    val label_flag_newCurrencyPurchaseAmount: Int
    val label_flag_preferredProvider: Int
    val label_flag_preferredUsdcLiquidityPool: Int
    val label_flag_requireCoinbaseEmailVerification: Int
    val label_flag_supportedOnRampProviders: Int
    val label_flag_withdrawalFeeAmount: Int
}
