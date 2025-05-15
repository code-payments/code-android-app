package com.flipcash.app.shareable.internal

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.flipcash.app.core.money.formatted
import com.flipcash.app.core.util.Linkify
import com.flipcash.app.shareable.ShareResult
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.shareable.ShareSheetController.Companion.ACTION_CASH_LINK_SHARED
import com.flipcash.app.shareable.ShareSheetController.Companion.ACTION_SHARE_CASH_LINK
import com.flipcash.app.shareable.Shareable
import com.flipcash.app.shareable.ShareablePendingData
import com.flipcash.shared.shareable.R
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.accounts.entropy
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.util.resources.ResourceHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import java.security.SecureRandom


internal class InternalShareSheetController(
    @ApplicationContext
    private val context: Context,
    private val clipboardManager: ClipboardManager,
    private val resources: ResourceHelper,
) : ShareSheetController {

    private var isChecking = false
    private var sharedWithApp: String? = null

    private var pendingShareable: Shareable? = null

    override var onShared: ((ShareResult) -> Unit)? = null

    override fun checkForShare() {
        pendingShareable?.let { shareable ->
            // if isChecking was never flipped, abort
            if (!isChecking) return

            when (shareable) {
                is Shareable.CashLink -> {
                    // if it was shared with an app, return successfully
                    if (sharedWithApp != null) {
                        onShared?.invoke(ShareResult.SharedToApp(sharedWithApp!!))
                        return
                    }

                    if (clipboardManager.hasPrimaryClip()) {
                        val clippedText = clipboardManager.primaryClip?.getItemAt(0)?.text
                        val pendingEntropy = shareable.pendingData?.entropy.orEmpty()
                        if (clippedText?.contains(pendingEntropy) == true) {
                            onShared?.invoke(ShareResult.CopiedToClipboard)
                        }
                    }

                    onShared?.invoke(ShareResult.NotShared)
                }

                Shareable.DownloadLink -> Unit
            }
        }
    }

    private val shareResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.getStringExtra(Intent.EXTRA_CHOSEN_COMPONENT)
            if (packageName != null) {
                sharedWithApp = packageName
                onShared?.invoke(ShareResult.SharedToApp(packageName))
            }
        }
    }

    override suspend fun present(shareable: Shareable) {
        when (shareable) {
            is Shareable.CashLink -> {
                val pendingData = ShareablePendingData.CashLink(
                    entropy = shareable.giftCardAccount.entropy,
                    amount = shareable.amount
                )

                pendingShareable = shareable.copy(pendingData = pendingData)

                shareCashLink(shareable.giftCardAccount, shareable.amount)
                delay(300)
                isChecking = true
                LocalBroadcastManager.getInstance(context).registerReceiver(
                    shareResultReceiver,
                    IntentFilter(ACTION_CASH_LINK_SHARED)
                )
            }

            Shareable.DownloadLink -> {
                shareDownloadLink()
            }
        }
    }

    private fun shareCashLink(
        giftCardAccount: GiftCardAccount,
        amount: LocalFiat,
    ) {
        val url = Linkify.cashLink(giftCardAccount.entropy)

        val text = "${amount.formatted()} $url"
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(
                Intent.EXTRA_TITLE,
                resources.getString(R.string.title_shareCashLink, amount.formatted(truncated = true))
            )
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

        val share = Intent.createChooser(intent, null).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(Intent.EXTRA_CHOSEN_COMPONENT_INTENT_SENDER, pi.intentSender)
        }

        context.startActivity(share)
    }

    private fun shareDownloadLink() {
        val shareRef = resources.getString(R.string.app_download_link_share_ref)
        val url = resources.getString(R.string.app_download_link_with_ref, shareRef)
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TITLE,
                resources.getString(R.string.title_shareDownloadLink))
            putExtra(Intent.EXTRA_TEXT, url)
            type = "text/plain"
        }

        val share = Intent.createChooser(intent, null).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        context.startActivity(share)
    }

    override fun reset() {
        println("share sheet reset")
        sharedWithApp = null
        isChecking = false
        onShared = null
        LocalBroadcastManager.getInstance(context).unregisterReceiver(shareResultReceiver)
    }
}