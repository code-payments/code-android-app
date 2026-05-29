package com.flipcash.app.invite.internal

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import com.flipcash.app.core.util.Linkify
import com.flipcash.app.core.util.MessagingPackages
import com.flipcash.app.invite.InviteChannel
import com.flipcash.app.invite.InviteChannelType
import com.flipcash.app.invite.InviteController
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.shareable.Shareable
import com.flipcash.shared.invite.R
import com.getcode.util.resources.ResourceHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

internal class InternalInviteController(
    @param:ApplicationContext
    private val context: Context,
    private val resources: ResourceHelper,
    private val shareController: ShareSheetController,
) : InviteController {

    private val _channels = MutableStateFlow(resolveChannels())
    override val channels: StateFlow<List<InviteChannel>> = _channels

    override fun refresh() {
        _channels.value = resolveChannels()
    }

    private fun resolveChannels(): List<InviteChannel> = buildList {
        val pm = context.packageManager

        // SMS — always present
        val smsIntent = Intent(Intent.ACTION_SENDTO, "smsto:".toUri())
        val smsApps = pm.queryIntentActivities(smsIntent, 0)
        val smsInfo = smsApps.firstOrNull()
        add(
            InviteChannel(
                label = smsInfo?.loadLabel(pm)?.toString() ?: "SMS",
                icon = smsInfo?.loadIcon(pm),
                type = InviteChannelType.Sms,
            )
        )

        // WhatsApp
        for (pkg in MessagingPackages.whatsApp) {
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                add(
                    InviteChannel(
                        label = pm.getApplicationLabel(appInfo).toString(),
                        icon = pm.getApplicationIcon(appInfo),
                        type = InviteChannelType.WhatsApp(pkg),
                    )
                )
                break // only show one WhatsApp variant
            } catch (_: PackageManager.NameNotFoundException) {
                // not installed
            }
        }

        // More (fallback)
        add(
            InviteChannel(
                label = resources.getString(R.string.action_inviteMoreOptions),
                icon = null,
                type = InviteChannelType.More,
            )
        )
    }

    private fun inviteMessage(): String {
        val shareRef = resources.getString(R.string.app_download_link_share_ref)
        val url = Linkify.download(shareRef)
        return resources.getString(R.string.message_invite_contact, url)
    }

    override fun launch(channel: InviteChannel, phoneNumber: String) {
        when (channel.type) {
            is InviteChannelType.Sms -> launchSms(phoneNumber)
            is InviteChannelType.WhatsApp -> launchWhatsApp(channel.type.packageName, phoneNumber)
            is InviteChannelType.More -> launchMore()
        }
    }

    private fun launchSms(phoneNumber: String) {
        val intent = Intent(
            Intent.ACTION_SENDTO,
            "smsto:$phoneNumber".toUri()
        ).apply {
            putExtra("sms_body", inviteMessage())
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            launchMore()
        }
    }

    private fun launchWhatsApp(packageName: String, phoneNumber: String) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Linkify.whatsApp(phoneNumber, inviteMessage()).toUri()
        ).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            launchMore()
        }
    }

    private fun launchMore() {
        runBlocking { shareController.present(Shareable.Invite) }
    }
}
