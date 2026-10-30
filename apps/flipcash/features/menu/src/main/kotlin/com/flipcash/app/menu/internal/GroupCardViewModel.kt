package com.flipcash.app.menu.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.analytics.GroupInviteMethod
import com.flipcash.analytics.events.GroupEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.share.TipCodeExportFormat
import com.flipcash.app.core.share.groupcard.GroupCardExporter
import com.flipcash.app.core.share.groupcard.QrMatrix
import com.flipcash.app.core.share.groupcard.groupCardBaseName
import com.flipcash.app.core.util.Linkify
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.shareable.Shareable
import com.flipcash.features.menu.R
import com.flipcash.shared.chat.GroupCardLinks
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

/**
 * A group's scan card: a link as a QR, exportable as an image. The link is the one the Invite
 * People sheet beneath was opened for, handed over in memory through [GroupCardLinks] because the
 * route is saved and an invite link is a key. With nothing handed over (a card restored after
 * process death) it is the plain link [Linkify.groupChatInvite] builds.
 */
@HiltViewModel(assistedFactory = GroupCardViewModel.Factory::class)
internal class GroupCardViewModel @AssistedInject constructor(
    @Assisted private val group: AppRoute.Menu.GroupCard,
    private val exporter: GroupCardExporter,
    private val shareable: ShareSheetController,
    private val resources: ResourceHelper,
    private val analytics: FlipcashAnalytics,
    links: GroupCardLinks,
) : ViewModel() {

    private val link: GroupCardLinks.Link? = links.cardLink(group.chatId, Linkify.groupChatInvite(group.chatId))

    /** The invite link this card encodes, for the lines under the QR; null for the plain link. */
    val inviteLink: GroupCardLinks.Link? = link?.takeIf { it.isInvite }

    /** Null only for a plain link from an id with no UUID form, which no group has. */
    val inviteUrl: String? = link?.url

    /** Null when [inviteUrl] is, or if the link cannot be encoded. */
    val matrix: QrMatrix? = inviteUrl?.let { runCatching { QrMatrix.encodeGroupCard(it) }.getOrNull() }

    init {
        analytics.track(GroupEvents.cardShown(group.memberCount.toInt()))
    }

    @AssistedFactory
    interface Factory {
        fun create(group: AppRoute.Menu.GroupCard): GroupCardViewModel
    }

    /** Asks which format to export, then renders it. */
    fun download() {
        if (matrix == null) return
        BottomBarManager.showMessage(
            title = resources.getString(R.string.title_downloadTipCardAs),
            actions = downloadOptions(resources) { format -> export(format) },
            showCancel = false,
            showScrim = true,
        )
    }

    private fun export(format: TipCodeExportFormat) {
        val matrix = matrix ?: return
        viewModelScope.launch {
            val baseName = groupCardBaseName(
                title = group.title,
                joinFormat = { resources.getString(R.string.label_groupCardFileName, it) },
                fallback = resources.getString(R.string.label_groupCardFileNameFallback),
            )
            val export = exporter.export(matrix, format, baseName)
            if (export == null) {
                BottomBarManager.showMessage(
                    title = resources.getString(R.string.error_title_tipCardExportFailed),
                    message = resources.getString(R.string.error_description_groupCardExportFailed),
                )
                return@launch
            }
            analytics.track(GroupEvents.inviteShared(GroupInviteMethod.DOWNLOAD))
            shareable.present(Shareable.TipCodeImage(export = export, title = baseName))
        }
    }
}
