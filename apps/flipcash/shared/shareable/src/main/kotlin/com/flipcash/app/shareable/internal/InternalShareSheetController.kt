package com.flipcash.app.shareable.internal

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.net.toUri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.flipcash.app.core.money.formatted
import com.flipcash.app.core.util.Linkify
import com.flipcash.app.core.util.MessagingPackages
import com.flipcash.app.core.tipping.TipCardOwner
import com.flipcash.app.shareable.ShareResult
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.shareable.ShareSheetController.Companion.ACTION_CASH_LINK_SHARED
import com.flipcash.app.shareable.ShareSheetController.Companion.ACTION_SHARE_CASH_LINK
import com.flipcash.app.shareable.Shareable
import com.flipcash.app.shareable.ShareablePendingData.CashLink
import com.flipcash.shared.shareable.R
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.accounts.entropy
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.util.resources.ResourceHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import java.security.SecureRandom
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.schedule
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


internal class InternalShareSheetController(
    @ApplicationContext
    private val context: Context,
    private val clipboardManager: ClipboardManager,
    private val resources: ResourceHelper,
) : ShareSheetController {

    private var checkingTimer: TimerTask? = null

    private var isChecking = false
        set(value) {
            val tmp = field
            field = value
            if (tmp != value) {
                if (value) {
                    // only allow isChecking to remain true for 30s,
                    // to ensure this doesn't remain true for an extended period of time while
                    // the user is sending the cash link in their desired app target and then returning back
                    // at a later time.
                    checkingTimer = Timer().schedule(30.seconds.inWholeMilliseconds) {
                        isChecking = false
                    }
                } else {
                    checkingTimer?.cancel()
                    checkingTimer = null
                }
            }
        }
    override val isCheckingForShare: Boolean
        get() = isChecking

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
                            return
                        }
                    }

                    onShared?.invoke(ShareResult.NotShared)
                }

                Shareable.DownloadLink -> Unit
                is Shareable.TokenInfo -> Unit
                is Shareable.Invite -> Unit
                is Shareable.TipCard -> Unit
                is Shareable.TipCodeImage -> Unit
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
                val pendingData = CashLink(
                    entropy = shareable.giftCardAccount.entropy,
                    amount = shareable.amount
                )

                pendingShareable = shareable.copy(pendingData = pendingData)

                shareCashLink(shareable.giftCardAccount, shareable.amount)
                delay(300.milliseconds)
                isChecking = true
                LocalBroadcastManager.getInstance(context).registerReceiver(
                    shareResultReceiver,
                    IntentFilter(ACTION_CASH_LINK_SHARED)
                )
            }

            Shareable.DownloadLink -> {
                shareDownloadLink()
            }

            is Shareable.TokenInfo -> {
                shareToken(shareable.token)
            }

            is Shareable.Invite -> {
                shareInviteLink()
            }

            is Shareable.TipCard -> shareTipCard(shareable)

            is Shareable.TipCodeImage -> shareTipCodeImage(shareable)
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
                resources.getString(
                    R.string.title_shareCashLink,
                    amount.formatted(Fiat.FormattingRule.Truncated)
                )
            )
            putExtra(
                Intent.EXTRA_SUBJECT,
                resources.getString(
                    R.string.title_shareCashLink,
                    amount.formatted(Fiat.FormattingRule.Truncated)
                )
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
        val url = Linkify.download(shareRef)
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(
                Intent.EXTRA_TITLE,
                resources.getString(R.string.title_shareDownloadLink)
            )
            putExtra(Intent.EXTRA_TEXT, url)
            type = "text/plain"
        }

        val share = Intent.createChooser(intent, null).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        context.startActivity(share)
    }

    private fun shareInviteLink() {
        val shareRef = resources.getString(R.string.app_download_link_share_ref)
        val url = Linkify.download(shareRef)
        val message = resources.getString(R.string.message_invite_contact, url)

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, message)
            type = "text/plain"
        }

        val chooser = Intent.createChooser(sendIntent, null).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val pm = context.packageManager
            val messagingPackages = buildSet {
                val messagingIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_MESSAGING)
                }
                pm.queryIntentActivities(messagingIntent, 0)
                    .forEach { add(it.activityInfo.packageName) }

                val smsIntent = Intent(Intent.ACTION_SENDTO, "smsto:".toUri())
                pm.queryIntentActivities(smsIntent, 0)
                    .forEach { add(it.activityInfo.packageName) }

                addAll(MessagingPackages.all)
            }

            val excludedComponents = pm.queryIntentActivities(sendIntent, 0)
                .filter { it.activityInfo.packageName !in messagingPackages }
                .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
                .toTypedArray()

            if (excludedComponents.isNotEmpty()) {
                chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excludedComponents)
            }
        }

        context.startActivity(chooser)
    }

    private fun shareToken(token: Token) {
        val url = Linkify.tokenInfo(token)
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(
                Intent.EXTRA_TITLE,
                resources.getString(R.string.title_shareToken, token.name)
            )
            putExtra(
                Intent.EXTRA_SUBJECT,
                resources.getString(R.string.title_shareToken, token.name)
            )
            putExtra(Intent.EXTRA_TEXT, url)
            type = "text/plain"
        }

        val share = Intent.createChooser(intent, null).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        context.startActivity(share)
    }

    private fun shareTipCard(shareable: Shareable.TipCard) {
        // Addressed the same way the You tab's link row addresses it, so what gets shared is what
        // the card says it is.
        val url = Linkify.tipcard(
            TipCardOwner.preferringUsername(shareable.username, shareable.userId)
        )
        val preview = shareable.preview

        val intent = Intent(Intent.ACTION_SEND).apply {
            // The shared payload is the link; the recipient still resolves its own preview from the
            // URL's OG tags. type stays text/plain and the bitmap is NOT an EXTRA_STREAM.
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            shareable.title?.let { putExtra(Intent.EXTRA_TITLE, it) }

            if (preview != null) {
                // Android draws the Sharesheet thumbnail from the intent's ClipData (a content:// URI),
                // not from any extra. On API < 29 there's no preview surface — this is simply ignored.
                // The Sharesheet's preview thumbnail slot is square, so use the 1:1 render (the hero
                // is for URL-unfurl surfaces the recipient resolves from OG tags, not this slot). The
                // ClipData description is a non-displayed a11y/clipboard label.
                clipData = ClipData.newUri(context.contentResolver, "Tip code", preview.squareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        val share = Intent.createChooser(intent, null).apply {
            // Use addFlags, not `flags =`. createChooser migrates the target's ClipData *and* its
            // read-permission grant onto the chooser intent; assigning `flags` here would wipe that
            // grant, and the Sharesheet's own preview process would be denied access to the image.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (preview != null) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(share)
    }

    /**
     * Shares the exported code file itself (PNG/SVG). Unlike [shareTipCard], the payload here IS the
     * file: it goes out as an `EXTRA_STREAM` of the export's own MIME type, so "save to Files" and
     * image-consuming targets receive something real rather than a link.
     */
    private fun shareTipCodeImage(shareable: Shareable.TipCodeImage) {
        val export = shareable.export

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = export.mimeType
            putExtra(Intent.EXTRA_STREAM, export.uri)
            shareable.title?.let {
                putExtra(Intent.EXTRA_TITLE, it)
                putExtra(Intent.EXTRA_SUBJECT, it)
            }
            // Also as ClipData so the Sharesheet can draw a preview of the PNG (and so the read
            // grant travels with the intent).
            clipData = ClipData.newUri(context.contentResolver, shareable.title.orEmpty(), export.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val share = Intent.createChooser(intent, null).apply {
            // addFlags, not `flags =` — see shareTipCard: assigning would wipe the migrated grant.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(share)
    }

    override fun reset(setChecked: Boolean) {
        pendingShareable = null
        sharedWithApp = null
        if (isChecking != setChecked) {
            isChecking = setChecked
        }
        onShared = null
        LocalBroadcastManager.getInstance(context).unregisterReceiver(shareResultReceiver)
    }
}