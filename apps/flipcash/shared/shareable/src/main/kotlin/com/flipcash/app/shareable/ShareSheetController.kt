package com.flipcash.app.shareable

import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.financial.LocalFiat


sealed interface ShareResult {
    data object NotShared: ShareResult
    data object CopiedToClipboard: ShareResult
    data class SharedToApp(val to: String): ShareResult
}

sealed interface Shareable {
    val pendingData: ShareablePendingData?
    data class CashLink(
        val giftCardAccount: GiftCardAccount,
        val amount: LocalFiat,
        override val pendingData: ShareablePendingData.CashLink? = null
    ): Shareable
}

sealed interface ShareablePendingData {
    data class CashLink(
        val entropy: String,
        val amount: LocalFiat,
    ): ShareablePendingData
}

interface ShareSheetController {
    var onShared: ((ShareResult) -> Unit)?
    fun checkForShare()
    suspend fun present(shareable: Shareable)
    fun reset()

    companion object {
        const val ACTION_SHARE_CASH_LINK = "com.flipcash.app.ACTION_SHARE_CASH_LINK"
        const val ACTION_CASH_LINK_SHARED = "com.flipcash.app.ACTION_CASH_LINK_SHARED"
    }
}