package com.flipcash.shared.transactionhistory.resources

/**
 * `Int` IDs for the transaction-history strings that common code hands to
 * [com.getcode.util.resources.ResourceHelper] and [com.getcode.util.resources.stringRes]. Android
 * returns the `R` IDs; desktop registers the generated Compose resources.
 */
@Suppress("PropertyName")
expect object TxStrings {
    val label_activity_convertFee: Int
    val label_txnDetails_from: Int
    val label_txnDetails_inPerson: Int
    val label_txnDetails_paidWith: Int
    val label_txnDetails_soldFor: Int
    val label_txnDetails_status_completed: Int
    val label_txnDetails_status_failed: Int
    val label_txnDetails_status_pending: Int
    val label_txnDetails_status_unknown: Int
    val label_txnDetails_to: Int
    val title_activity_convert: Int
    val title_activity_receivedFrom: Int
    val title_activity_sentTo: Int
    val title_activity_tipFrom: Int
    val title_txnDetails_buy: Int
    val title_txnDetails_convert: Int
    val title_txnDetails_deposit: Int
    val title_txnDetails_poolPayment: Int
    val title_txnDetails_sell: Int
    val title_txnDetails_unknown: Int
    val title_txnDetails_withdraw: Int
    val title_txnDetails_youGaveCash: Int
    val title_txnDetails_youReceived: Int
    val title_txnDetails_youReceivedCash: Int
    val title_txnDetails_youSent: Int
    val title_txnDetails_youSentCashLink: Int
    val title_txnDetails_youTipped: Int
}
