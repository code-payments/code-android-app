package com.flipcash.shared.transactionhistory.resources

import com.flipcash.shared.transactionhistory.generated.resources.Res
import com.flipcash.shared.transactionhistory.generated.resources.label_activity_convertFee
import com.flipcash.shared.transactionhistory.generated.resources.label_txnDetails_from
import com.flipcash.shared.transactionhistory.generated.resources.label_txnDetails_inPerson
import com.flipcash.shared.transactionhistory.generated.resources.label_txnDetails_paidWith
import com.flipcash.shared.transactionhistory.generated.resources.label_txnDetails_soldFor
import com.flipcash.shared.transactionhistory.generated.resources.label_txnDetails_status_completed
import com.flipcash.shared.transactionhistory.generated.resources.label_txnDetails_status_failed
import com.flipcash.shared.transactionhistory.generated.resources.label_txnDetails_status_pending
import com.flipcash.shared.transactionhistory.generated.resources.label_txnDetails_status_unknown
import com.flipcash.shared.transactionhistory.generated.resources.label_txnDetails_to
import com.flipcash.shared.transactionhistory.generated.resources.title_activity_convert
import com.flipcash.shared.transactionhistory.generated.resources.title_activity_receivedFrom
import com.flipcash.shared.transactionhistory.generated.resources.title_activity_sentTo
import com.flipcash.shared.transactionhistory.generated.resources.title_activity_tipFrom
import com.flipcash.shared.transactionhistory.generated.resources.title_txnDetails_buy
import com.flipcash.shared.transactionhistory.generated.resources.title_txnDetails_convert
import com.flipcash.shared.transactionhistory.generated.resources.title_txnDetails_deposit
import com.flipcash.shared.transactionhistory.generated.resources.title_txnDetails_poolPayment
import com.flipcash.shared.transactionhistory.generated.resources.title_txnDetails_sell
import com.flipcash.shared.transactionhistory.generated.resources.title_txnDetails_unknown
import com.flipcash.shared.transactionhistory.generated.resources.title_txnDetails_withdraw
import com.flipcash.shared.transactionhistory.generated.resources.title_txnDetails_youGaveCash
import com.flipcash.shared.transactionhistory.generated.resources.title_txnDetails_youReceived
import com.flipcash.shared.transactionhistory.generated.resources.title_txnDetails_youReceivedCash
import com.flipcash.shared.transactionhistory.generated.resources.title_txnDetails_youSent
import com.flipcash.shared.transactionhistory.generated.resources.title_txnDetails_youSentCashLink
import com.flipcash.shared.transactionhistory.generated.resources.title_txnDetails_youTipped
import com.getcode.util.resources.ComposeResourceIds

@Suppress("PropertyName")
actual object TxStrings {
    actual val label_activity_convertFee: Int = ComposeResourceIds.register(Res.string.label_activity_convertFee)
    actual val label_txnDetails_from: Int = ComposeResourceIds.register(Res.string.label_txnDetails_from)
    actual val label_txnDetails_inPerson: Int = ComposeResourceIds.register(Res.string.label_txnDetails_inPerson)
    actual val label_txnDetails_paidWith: Int = ComposeResourceIds.register(Res.string.label_txnDetails_paidWith)
    actual val label_txnDetails_soldFor: Int = ComposeResourceIds.register(Res.string.label_txnDetails_soldFor)
    actual val label_txnDetails_status_completed: Int = ComposeResourceIds.register(Res.string.label_txnDetails_status_completed)
    actual val label_txnDetails_status_failed: Int = ComposeResourceIds.register(Res.string.label_txnDetails_status_failed)
    actual val label_txnDetails_status_pending: Int = ComposeResourceIds.register(Res.string.label_txnDetails_status_pending)
    actual val label_txnDetails_status_unknown: Int = ComposeResourceIds.register(Res.string.label_txnDetails_status_unknown)
    actual val label_txnDetails_to: Int = ComposeResourceIds.register(Res.string.label_txnDetails_to)
    actual val title_activity_convert: Int = ComposeResourceIds.register(Res.string.title_activity_convert)
    actual val title_activity_receivedFrom: Int = ComposeResourceIds.register(Res.string.title_activity_receivedFrom)
    actual val title_activity_sentTo: Int = ComposeResourceIds.register(Res.string.title_activity_sentTo)
    actual val title_activity_tipFrom: Int = ComposeResourceIds.register(Res.string.title_activity_tipFrom)
    actual val title_txnDetails_buy: Int = ComposeResourceIds.register(Res.string.title_txnDetails_buy)
    actual val title_txnDetails_convert: Int = ComposeResourceIds.register(Res.string.title_txnDetails_convert)
    actual val title_txnDetails_deposit: Int = ComposeResourceIds.register(Res.string.title_txnDetails_deposit)
    actual val title_txnDetails_poolPayment: Int = ComposeResourceIds.register(Res.string.title_txnDetails_poolPayment)
    actual val title_txnDetails_sell: Int = ComposeResourceIds.register(Res.string.title_txnDetails_sell)
    actual val title_txnDetails_unknown: Int = ComposeResourceIds.register(Res.string.title_txnDetails_unknown)
    actual val title_txnDetails_withdraw: Int = ComposeResourceIds.register(Res.string.title_txnDetails_withdraw)
    actual val title_txnDetails_youGaveCash: Int = ComposeResourceIds.register(Res.string.title_txnDetails_youGaveCash)
    actual val title_txnDetails_youReceived: Int = ComposeResourceIds.register(Res.string.title_txnDetails_youReceived)
    actual val title_txnDetails_youReceivedCash: Int = ComposeResourceIds.register(Res.string.title_txnDetails_youReceivedCash)
    actual val title_txnDetails_youSent: Int = ComposeResourceIds.register(Res.string.title_txnDetails_youSent)
    actual val title_txnDetails_youSentCashLink: Int = ComposeResourceIds.register(Res.string.title_txnDetails_youSentCashLink)
    actual val title_txnDetails_youTipped: Int = ComposeResourceIds.register(Res.string.title_txnDetails_youTipped)
}
