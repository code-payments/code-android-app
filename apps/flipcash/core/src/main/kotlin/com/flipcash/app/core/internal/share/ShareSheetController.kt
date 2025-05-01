package com.flipcash.app.core.internal.share

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.flipcash.app.core.internal.Linkify
import com.flipcash.app.core.money.formatted
import com.flipcash.core.R
import com.getcode.opencode.controllers.BalanceController
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.accounts.entropy
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.util.resources.ResourceHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ShareResult {
    data object NotShared: ShareResult
    data object CopiedToClipboard: ShareResult
    data class SharedToApp(val to: String): ShareResult
}

@Singleton
class ShareSheetController @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val clipboardManager: ClipboardManager,
    private val resources: ResourceHelper,
    private val balanceController: BalanceController,
) {
    internal companion object {
        const val ACTION_SHARE_CASH_LINK = "com.flipcash.app.ACTION_SHARE_CASH_LINK"
        const val ACTION_CASH_LINK_SHARED = "com.flipcash.app.ACTION_CASH_LINK_SHARED"
    }

    private var isChecking = false
    private var sharedWithApp: String? = null

    private var pendingEntropy = ""
    private var pendingAmount: LocalFiat? = null

    var onShared: ((ShareResult) -> Unit)? = null

    fun checkForShare() {
        // if entropy from the gift card is not set, theres nothing to check for
        if (pendingEntropy.isEmpty()) return

        println("checking for share of $pendingEntropy")

        // if isChecking was never flipped, abort
        if (!isChecking) return

        // if it was shared with an app, return successfully
        if (sharedWithApp != null) {
            pendingAmount?.let { balanceController.subtract(it) }
            onShared?.invoke(ShareResult.SharedToApp(sharedWithApp!!))
            return
        }

        // if the entropy exists in the clipboard, return successfully
        if (clipboardManager.hasPrimaryClip()) {
            val clippedText = clipboardManager.primaryClip?.getItemAt(0)?.text
            if (clippedText?.contains(pendingEntropy) == true) {
                pendingAmount?.let { balanceController.subtract(it) }
                onShared?.invoke(ShareResult.CopiedToClipboard)
            }
        }

        onShared?.invoke(ShareResult.NotShared)
    }

    private val shareResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.getStringExtra(Intent.EXTRA_CHOSEN_COMPONENT)
            sharedWithApp = packageName
        }
    }

    suspend fun presentForCashLink(
        giftCardAccount: GiftCardAccount,
        amount: LocalFiat
    ) {
        pendingEntropy = giftCardAccount.entropy
        pendingAmount = amount
        shareCashLink(giftCardAccount, amount)
        delay(300)
        isChecking = true
        LocalBroadcastManager.getInstance(context).registerReceiver(
            shareResultReceiver,
            IntentFilter(ACTION_CASH_LINK_SHARED)
        )
    }

    private fun shareCashLink(
        giftCardAccount: GiftCardAccount,
        amount: LocalFiat,
    ) {
        val url = Linkify.cashLink(giftCardAccount.entropy)
        val suffix = amount.converted.currencyCode.takeIf {
            it != CurrencyCode.USD
        }?.let {
            resources.getString(R.string.subtitle_ofUsdSuffix)
        }
        val text = "${amount.formatted(suffix = suffix)} $url"
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }

        val random = SecureRandom()
        val requestCode = random.nextInt(256)


        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(ACTION_SHARE_CASH_LINK).apply {
                setPackage(context.packageName)
                putExtra("share_id", giftCardAccount.entropy)
            },
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val share = Intent.createChooser(intent, "Share via").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(Intent.EXTRA_CHOSEN_COMPONENT_INTENT_SENDER, pi.intentSender)
        }

        context.startActivity(share)
    }

    fun reset() {
        sharedWithApp = null
        isChecking = false
        onShared = null
        LocalBroadcastManager.getInstance(context).unregisterReceiver(shareResultReceiver)
    }
}