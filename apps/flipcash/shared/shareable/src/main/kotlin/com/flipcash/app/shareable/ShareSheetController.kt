package com.flipcash.app.shareable

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.financial.LocalFiat


sealed interface ShareResult {
    sealed interface ActionTaken
    data object NotShared: ShareResult
    data object CopiedToClipboard: ShareResult, ActionTaken
    data class SharedToApp(val to: String): ShareResult, ActionTaken
}

sealed interface Shareable {
    val pendingData: ShareablePendingData?
    data class CashLink(
        val giftCardAccount: GiftCardAccount,
        val amount: LocalFiat,
        override val pendingData: ShareablePendingData.CashLink? = null
    ): Shareable

    data object DownloadLink: Shareable {
        override val pendingData: ShareablePendingData? = null
    }
}

sealed interface ShareablePendingData {
    data class CashLink(
        val entropy: String,
        val amount: LocalFiat,
    ): ShareablePendingData
}

interface ShareSheetController {
    val isCheckingForShare: Boolean
    var onShared: ((ShareResult) -> Unit)?
    fun checkForShare()
    suspend fun present(shareable: Shareable)
    fun reset(setChecked: Boolean = false)

    companion object {
        const val ACTION_SHARE_CASH_LINK = "com.flipcash.app.ACTION_SHARE_CASH_LINK"
        const val ACTION_CASH_LINK_SHARED = "com.flipcash.app.ACTION_CASH_LINK_SHARED"
    }
}

private object NoOpController: ShareSheetController {
    override val isCheckingForShare: Boolean = false
    override var onShared: ((ShareResult) -> Unit)? = null
    override fun checkForShare() = Unit
    override suspend fun present(shareable: Shareable) = Unit
    override fun reset(setChecked: Boolean) = Unit
}

val LocalShareController: ProvidableCompositionLocal<ShareSheetController> = staticCompositionLocalOf { NoOpController }