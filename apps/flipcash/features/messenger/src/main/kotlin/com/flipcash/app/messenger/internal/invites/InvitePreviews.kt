package com.flipcash.app.messenger.internal.invites

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.GroupInviteViewModel
import com.flipcash.app.messenger.internal.screens.GroupInviteSheet
import com.flipcash.app.messenger.internal.screens.ShareToChatsSubject
import com.flipcash.app.messenger.internal.screens.profile.NoJoinRequests
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.Invite
import com.flipcash.services.models.chat.InviteId
import com.flipcash.shared.chat.HostInvite
import com.flipcash.shared.chat.InviteExpiry
import com.flipcash.shared.chat.InviteSettings
import com.flipcash.shared.chat.InviteUses
import com.flipcash.shared.chat.ui.ConversationReference
import com.getcode.manager.BottomBarManager
import com.getcode.ui.components.bars.BottomBarView
import com.getcode.util.resources.AndroidResourceHelper
import com.getcode.util.resources.ResourceHelper
import org.jetbrains.compose.resources.StringResource
import com.getcode.util.resources.ResourceType
import java.io.File
import kotlin.time.Instant

/*
 * One preview per frame state of the invite flow. They are internal and parameterless so the
 * screenshot test draws the same composables.
 */

private val PreviewChat = ChatId(ByteArray(32) { 3 })
private val Group = ChatSubject.Group(
    chatId = PreviewChat,
    groupTitle = "Bad Boys",
    picture = null,
    memberCount = 412L,
    rules = null,
    isMember = true,
)
private const val Url = "https://app.flipcash.com/chat/invite#s=AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCdEf"
private val Expiry = Instant.fromEpochSeconds(1_794_000_000)

private val RecentChats = listOf(
    ConversationReference(chatId = ChatId(ByteArray(32) { 1 }), title = "Bad Boys 2", isGroup = true),
    ConversationReference(chatId = ChatId(ByteArray(32) { 2 }), displayName = "Dana"),
    ConversationReference(chatId = ChatId(ByteArray(32) { 4 }), title = "Coffee Crew", isGroup = true),
)

private fun invite(
    n: Int,
    status: Invite.Status = Invite.Status.ACTIVE,
    useCount: Long = 0,
    maxUses: Long = 0,
    expiresAt: Instant? = Expiry,
    url: String? = Url,
) = HostInvite(
    Invite(
        id = InviteId(ByteArray(32) { n.toByte() }),
        chatId = PreviewChat,
        createdBy = List(32) { 1 },
        maxUses = maxUses,
        useCount = useCount,
        createdAt = Instant.fromEpochSeconds(1_790_000_000L - n * 86_400L),
        expiresAt = expiresAt,
        status = status,
        secretEnvelope = null,
    ),
    url,
)

private val Active = invite(1, useCount = 3, maxUses = 10)
private val Inactive = listOf(
    invite(2, status = Invite.Status.EXPIRED),
    invite(3, status = Invite.Status.REVOKED, expiresAt = null),
)

@Composable
private fun rememberPreviewResources(): ResourceHelper {
    val context = LocalContext.current
    return remember(context) { PreviewResources(context) }
}

/** Draws whatever [show] puts on the bottom bar, as the app's host does. */
@Composable
private fun BottomBarFrame(show: (ResourceHelper) -> Unit) {
    val resources = rememberPreviewResources()
    val message = remember {
        BottomBarManager.clear()
        show(resources)
        BottomBarManager.messages.value.last().also { BottomBarManager.clear() }
    }
    BottomBarView(bottomBarMessage = message, onShown = {}, onClose = {}, onBackPressed = {})
}

@Composable
private fun InviteSheetFrame(subject: ShareToChatsSubject, selection: List<ChatId> = emptyList()) {
    GroupInviteSheet(
        inviteUrl = if (subject is ShareToChatsSubject.Group) Url.removePrefix("https://app.flipcash.com/chat/invite#s=") else null,
        subject = subject,
        state = GroupInviteViewModel.State(
            recentChats = RecentChats,
            selection = selection,
            encryptedOnly = subject is ShareToChatsSubject.GroupInviteLink,
            encryptedChats = RecentChats.map { it.chatId }.toSet(),
        ),
        onShare = {},
        onCopy = {},
        onToggle = {},
        onMessageChanged = {},
        onInvite = {},
        onDismiss = {},
        onShowCard = if (subject is ShareToChatsSubject.Group) ({}) else null,
    )
}

/** Frame 1a, node 12844:60. */
@Preview(heightDp = 520)
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
internal fun InviteFrame1aHostSheet() = BottomBarFrame {
    showInviteHostSheet(it, "Bad Boys", {}, {}, {})
}

/** Frame 1b, node 12933:210: the first tap is making the link. */
@Preview(heightDp = 817)
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
internal fun InviteFrame1bCreating() =
    InviteSheetFrame(ShareToChatsSubject.GroupInviteLink(Group, InviteLinkState.Creating))

/** Frame 1c, node 12844:161. */
@Preview(heightDp = 400)
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
internal fun InviteFrame1cTooMany() = BottomBarFrame { showTooManyInviteLinks(it) }

/** Frame 1d, node 12844:185: Join Requests' empty state for a viewer who can invite. */
@Preview(heightDp = 600)
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
internal fun InviteFrame1dJoinRequestsEmpty() =
    NoJoinRequests(groupName = "Bad Boys", canInvite = true, onShare = {})

/** Frame 1e, node 12855:117. */
@Preview(heightDp = 400)
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
internal fun InviteFrame1eNotAvailable() = BottomBarFrame { showInviteLinksNotAvailable(it) {} }

/** Frame 1f, node 12933:113: the invite variant with a link. */
@Preview(heightDp = 817)
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
internal fun InviteFrame1fReady() = InviteSheetFrame(
    ShareToChatsSubject.GroupInviteLink(Group, InviteLinkState.Ready(Url, Expiry, 0, 0)),
)

/** Frame 1g, node 12933:313: the join variant, unchanged. */
@Preview(heightDp = 817)
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
internal fun InviteFrame1gJoin() = InviteSheetFrame(ShareToChatsSubject.Group(Group))

/** Frame 2a, node 12844:200. */
@Preview(heightDp = 817)
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
internal fun InviteFrame2aManage() = ManageInvitesContent(
    state = ManageInvitesViewModel.State(
        loading = false,
        active = listOf(Active, invite(4, url = null)),
        inactive = Inactive,
        current = Active,
    ),
    onBack = {},
    onRow = {},
    onNewLink = {},
)

/** Frame 2b, node 12886:56. */
@Preview(heightDp = 300)
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
internal fun InviteFrame2bRowSheet() =
    InviteLinkActions(invite = Active, onShare = {}, onRevoke = {}, onDismiss = {})

/** Frame 2c, node 12844:288. */
@Preview(heightDp = 400)
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
internal fun InviteFrame2cRevoke() = BottomBarFrame { showRevokeInviteLink(it) {} }

/** Frame 2d, node 12844:334. */
@Preview(heightDp = 560)
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
internal fun InviteFrame2dNewLink() =
    NewInviteLinkContent(onCreate = {}, onDismiss = {}, initial = InviteSettings.Default)

/** Frame 2d with presets other than the defaults chosen, so the chip highlight shows off its start. */
@Preview(heightDp = 560)
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
internal fun InviteFrame2dNewLinkChosen() = NewInviteLinkContent(
    onCreate = {},
    onDismiss = {},
    initial = InviteSettings(InviteExpiry.ThirtyDays, InviteUses.Ten),
)

/** Frame 2e, node 12844:395. */
@Preview(heightDp = 817)
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
internal fun InviteFrame2eEmpty() = ManageInvitesContent(
    state = ManageInvitesViewModel.State(loading = false),
    onBack = {},
    onRow = {},
    onNewLink = {},
)

/** Strings only: a preview has no injected resources. */
private class PreviewResources(private val context: Context) : AndroidResourceHelper {
    override fun getString(resourceId: Int): String = context.getString(resourceId)
    override fun getString(resourceId: Int, vararg formatArgs: Any): String =
        context.getString(resourceId, *formatArgs)
    override fun getString(resource: StringResource, vararg formatArgs: Any): String = ""
    override fun getRawResource(resourceId: Int): String = ""
    override fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any, default: String): String = default
    override fun getDimension(dimenId: Int, default: Float): Float = default
    override fun getDimensionPixelSize(dimenId: Int, default: Int): Int = default
    override fun getDir(name: String, mode: Int): File? = null
    override val displayMetrics: DisplayMetrics get() = context.resources.displayMetrics
    override fun getDrawable(drawableResId: Int): Drawable? = null
    override fun getIdentifier(name: String, type: ResourceType): Int? = null
    override fun getFont(fontResId: Int): Typeface? = null
}
