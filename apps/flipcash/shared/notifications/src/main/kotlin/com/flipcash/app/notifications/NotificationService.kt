package com.flipcash.app.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.RingtoneManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.flipcash.app.core.media.MediaUrlResolver
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.contacts.ContactResolver
import com.flipcash.app.core.util.Linkify
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.persistence.sources.UserProfileDataSource
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.controllers.PushController
import com.getcode.opencode.model.core.ID
import com.flipcash.services.models.SocialAccount
import com.flipcash.services.models.handle
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.NavigationTrigger
import com.flipcash.services.models.NotificationCategory
import com.flipcash.services.models.NotificationPayload
import com.flipcash.services.models.PushChatMetadata
import com.flipcash.services.models.Substitution
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.user.UserManager
import com.flipcash.shared.notifications.R
import com.getcode.utils.TraceType
import com.getcode.utils.hexEncodedString
import com.getcode.utils.trace
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@AndroidEntryPoint
class NotificationService : FirebaseMessagingService(),
    CoroutineScope by CoroutineScope(Dispatchers.IO) {

    companion object {
        private const val KEY_TITLE = "push_notification_title"
        private const val KEY_BODY = "push_notification_body"
        private const val KEY_PAYLOAD = "flipcash_payload"

        /**
         * Request code for the content intent of any notification that only launches the app.
         * Arbitrary, and shared on purpose — every such intent is identical, so it does not
         * matter which notification's the system keeps.
         */
        private const val LAUNCH_REQUEST_CODE = 99

        // Correlation id for measurement runs. scripts/spike/ sends a known
        // sequence number with every push so a missing delivery and a late one
        // can be told apart in the log rather than reading as the same silence.
        // No production sender sets it, so `seq` is empty in the field.
        private const val KEY_SPIKE_SEQ = "spike_seq"

        // Upper bound on how long we'll wait for a remote avatar before posting
        // without one. A memory/disk cache hit returns well under this; the
        // bound only caps the cold-cache network fetch so the notification isn't
        // held back indefinitely.
        private const val AVATAR_FETCH_TIMEOUT_MS = 5_000L
    }

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var pushController: PushController

    @Inject
    lateinit var mediaUrlResolver: MediaUrlResolver

    @Inject
    lateinit var notificationManager: NotificationManagerCompat

    @Inject
    lateinit var tokenCoordinator: TokenCoordinator

    @Inject
    lateinit var contactCoordinator: ContactCoordinator

    @Inject
    lateinit var contactResolver: ContactResolver

    @Inject
    lateinit var chatCoordinator: ChatCoordinator

    @Inject
    lateinit var profileController: ProfileController

    @Inject
    lateinit var userProfileDataSource: UserProfileDataSource

    @Inject
    lateinit var chatMetadataDataSource: ChatMetadataDataSource

    // TODO(firebase-messaging): 25.1.0 deprecated onNewToken in favor of FID-based onRegistered().
    //  Migrate once Firebase ships a stable guide and the backend accepts FID registration.
    //  Tracking: https://github.com/firebase/firebase-android-sdk/issues/8087
    @Suppress("DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        authenticateIfNeeded {
            launch {
                if (userManager.state.value.authState.canAccessAuthenticatedApis) {
                    pushController.addToken(token)
                        .onSuccess {
                            userManager.set(pushToken = token)
                            trace("push token updated onNewToken", type = TraceType.Silent)
                        }.onFailure {
                            trace(message = "Failure updating push token", error = it)
                        }
                }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.data[KEY_TITLE]?.ifEmpty { message.notification?.title }
        val body = message.data[KEY_BODY]?.ifEmpty { message.notification?.body }

        val payload = message.data.getOrDefault(KEY_PAYLOAD, "")
            .takeIf { it.isNotEmpty() }
            ?.let { NotificationPayload.fromEncoded(it) }

        val actions = planPushHandling(
            title = title,
            body = body,
            payload = payload,
        )

        val latencyMs = System.currentTimeMillis() - message.sentTime
        val bucket = applicationContext.currentStandbyBucket()

        trace(
            message = "onMessageReceived",
            type = TraceType.Process,
            metadata = {
                // Push content is not recorded: TraceType.Process is forwarded to
                // breadcrumb sinks, and message text does not belong in Bugsnag.
                "seq" to message.data[KEY_SPIKE_SEQ].orEmpty()
                "has_body" to (body != null)
                "actions" to actions.size
                "silent" to (title == null)
                "bucket" to bucket
                "latency_ms" to latencyMs
                "priority" to message.priority
                "original_priority" to message.originalPriority
            }
        )

        if (actions.isEmpty()) return

        execute(actions)
    }

    /** Runs a planned action list. Sync work starts immediately; posting a
     *  notification waits for authentication, as it always has. */
    private fun execute(actions: List<PushAction>) {
        if (PushAction.UpdateTokens in actions) {
            launch { tokenCoordinator.update() }
        }

        val chatActions = actions.filter {
            it is PushAction.RefreshFeed ||
                it is PushAction.LoadMessages ||
                it is PushAction.ApplyMessage
        }
        if (chatActions.isNotEmpty()) {
            launch {
                chatActions.forEach { action ->
                    when (action) {
                        is PushAction.RefreshFeed -> chatCoordinator.refreshFeed()
                        is PushAction.LoadMessages -> chatCoordinator.loadMessages(chatId = action.chatId)
                        is PushAction.ApplyMessage -> chatCoordinator.applyPushedMessage(
                            chatId = action.chatId,
                            message = action.message,
                        )
                        else -> Unit
                    }
                }
            }
        }

        val post = actions.filterIsInstance<PushAction.PostNotification>().firstOrNull()
        val syncContacts = actions.any { it is PushAction.SyncContacts }

        if (post == null && !syncContacts) return

        authenticateIfNeeded {
            launch {
                try {
                    if (syncContacts) launch { contactCoordinator.sync() }
                    if (post != null) {
                        val resolvedTitle =
                            applySubstitutions(post.title, post.payload?.titleSubstitutions.orEmpty())
                        val resolvedBody = post.body?.let {
                            applySubstitutions(it, post.payload?.bodySubstitutions.orEmpty())
                        }
                        postNotification(resolvedTitle, resolvedBody, post.payload)
                    }
                } catch (e: Exception) {
                    trace(tag = "NotificationService", message = "Failed to handle push", error = e)
                }
            }
        }
    }

    private suspend fun postNotification(title: String, body: String?, payload: NotificationPayload?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val category = payload?.category ?: NotificationCategory.DEFAULT
        NotificationChannels.ensureChannelGroups(this, notificationManager)
        val channel = NotificationChannels.channelFor(this, category)
        notificationManager.createNotificationChannel(channel)

        val chatId = when (val trigger = payload?.navigation) {
            is NavigationTrigger.Chat.ByContact -> null
            is NavigationTrigger.Chat.ById -> trigger.chatId
            is NavigationTrigger.CurrencyInfo -> null
            null -> null
        }

        if (chatId != null && chatCoordinator.isActiveChat(chatId)) return

        // Resolved once, here, because the tap target and the message style both turn on what
        // kind of chat this is and neither can take the payload's word for it.
        val styling = chatId?.let {
            planConversationStyling(
                payloadChatType = payload?.chatMetadata?.chatType,
                storedChatType = chatMetadataDataSource.getChatType(it),
                storedTitle = chatMetadataDataSource.getTitle(it),
            )
        }

        val isChat = chatId != null && styling != null
        val groupKey = payload?.groupKey?.takeIf { it.isNotEmpty() }
        val group = planNotificationGroup(payloadGroupKey = groupKey, isChat = isChat)

        val builder = NotificationCompat.Builder(this, channel.id)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setSmallIcon(R.drawable.flipcash_logo)
            .setColor(getColor(R.color.notification_color))
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(styling?.chatType, payload?.navigation))
            .apply {
                if (group != null) setGroup(group)
            }

        val notificationId = if (chatId != null && styling != null) {
            builder.applyChatStyle(chatId, styling, groupKey, title, body, payload?.chatMetadata)
        } else {
            builder.setContentTitle(title).setContentText(body)
            SecureRandom().nextInt(Int.MAX_VALUE)
        }

        notificationManager.notify(notificationId, builder.build())

        if (group != null) {
            val summary = NotificationCompat.Builder(this, channel.id)
                .setSmallIcon(R.drawable.flipcash_logo)
                .setColor(getColor(R.color.notification_color))
                .setGroup(group)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(group.hashCode(), summary)
        }
    }

    private suspend fun NotificationCompat.Builder.applyChatStyle(
        chatId: ChatId,
        styling: ConversationStyling,
        groupKey: String?,
        title: String?,
        body: String?,
        metadata: PushChatMetadata?,
    ): Int {
        val notificationId = chatId.hashCode()

        // Prefer the device-contact identity (CONTACT_DM, or a counterparty saved
        // in the address book): the user's own name + photo for them. The row is
        // keyed by DM chat id, so a group can never match it — don't ask.
        val contactE164 = if (!styling.isGroupConversation) {
            contactCoordinator.lookupContactByDmChatId(chatId.toString())?.e164
        } else {
            null
        }

        // Otherwise fall back to the sender's server-side profile — which is the only
        // identity a TIP_DM has, and the only one a group participant has at all.
        val lookup = planSenderLookup(
            chatType = styling.chatType,
            sendingUserId = metadata?.sendingUserId,
            hasDeviceContact = contactE164 != null,
        )
        val sender = when (lookup) {
            is SenderLookup.ByUserId ->
                resolveSenderProfile(lookup.userId)?.let { Sender(lookup.userId, it) }
            SenderLookup.OtherMember ->
                chatCoordinator.getOtherMember(chatId)?.let { Sender(it.userId, it.userProfile) }
            SenderLookup.None -> null
        }
        val e164 = contactE164 ?: sender?.profile?.verifiedPhoneNumber

        // Every name this sender is known by, not just the one rendered: the server composed the
        // body from one of them and the line is drawn with another, so stripping the sender's name
        // out of their own message needs all of them. See [planMessageBody].
        val senderNames = listOf(
            e164?.let { contactResolver.resolveName(it) },
            sender?.profile?.displayName,
            sender?.profile?.socialHandle(),
            title,
        )
        val senderName = senderNames.firstOrNull { !it.isNullOrBlank() }.orEmpty()

        // Device-contact photo (local, synchronous) first; otherwise the profile
        // picture URL loaded through the app's shared Coil loader (cache-first,
        // network-bounded). Works for every chat type: a group sender who is in the
        // address book resolves through their profile's phone number.
        //
        // Size the rendition to the platform's large-icon dimension (density-scaled) rather than
        // grabbing the smallest THUMBNAIL — the server ships several thumbnail/display sizes and
        // the tiny 32px one looks grainy on the notification's person icon.
        val avatarPx = resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
        val avatar = e164?.let { resolveContactPhoto(it) }
            ?: sender?.profile?.profilePicture?.let { picture ->
                // Through the resolver, not `picture.urlForSize` — the stored download URL expires
                // and the profile it came from may have been persisted days ago.
                mediaUrlResolver.urlForSize(
                    media = picture,
                    targetLongestSidePx = avatarPx,
                    // The sender's picture, so their profile is what authorizes a re-mint.
                    access = BlobAccessContext.profile(sender.userId),
                )?.let { url ->
                    loadRemoteAvatar(url, picture.cacheKeyForSize(avatarPx))
                }
            }

        trace(
            tag = "NotificationService",
            message = "applyChatStyle: chatId=$chatId, groupKey=$groupKey, chatType=${styling.chatType}, isGroup=${styling.isGroupConversation}, hasTitle=${styling.conversationTitle != null}, lookup=${lookup::class.simpleName}, e164=$e164, hasSender=${sender != null}, hasAvatar=${avatar != null}, authenticated=${userManager.accountCluster != null}",
            type = TraceType.Log,
        )

        val selfPerson = buildSelfPerson(this@NotificationService, userManager.profile, contactResolver)

        // Key the sender by their user id. MessagingStyle tells participants apart by
        // Person key, and groupKey is per-chat — under it every sender in a group
        // collapses into one person, so the name and icon of whoever spoke first get
        // stamped on all of their messages.
        val senderKey = metadata?.sendingUserId?.hexEncodedString() ?: groupKey ?: "unknown"

        val senderPerson = Person.Builder()
            .setName(senderName)
            .setKey(senderKey)
            .apply {
                if (avatar != null) setIcon(IconCompat.createWithBitmap(avatar.toCircularBitmap()))
            }
            .build()

        val style = notificationManager.activeNotifications
            .firstOrNull { it.id == notificationId }
            ?.notification
            ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
            ?: NotificationCompat.MessagingStyle(selfPerson)

        style.addMessage(planMessageBody(body, senderNames), System.currentTimeMillis(), senderPerson)

        // After the extract above, not before: a re-post rebuilds the style from the notification
        // already on screen, which carries the old flag and title back with it.
        style.setGroupConversation(styling.isGroupConversation)
        // Only when there is one to set, so a push that lands before the group's row syncs leaves
        // the title a previous push managed to resolve rather than blanking it.
        styling.conversationTitle?.let { style.setConversationTitle(it) }

        setStyle(style)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .addAction(buildReplyAction(chatId, notificationId))
            .addAction(buildMarkAsReadAction(chatId))

        return notificationId
    }

    /** Who a chat push is attributed to: their id (for blob access) and their profile. */
    private data class Sender(val userId: ID, val profile: UserProfile)

    /**
     * The sender's profile, cache-first.
     *
     * `user_profiles` is a superset of the chat roster —
     * [com.flipcash.app.persistence.sources.ChatMemberDataSource] writes every synced member's
     * profile into it — so this is a local read for anyone the app has already seen, and only
     * reaches the network for a group participant it hasn't. The result is written back so the
     * next push from them doesn't.
     *
     * The transcript's [com.flipcash.shared.chat.ChatCoordinator.requestSenderProfile] resolves
     * the same profiles, but it is fire-and-forget by design (it must not stall Paging), and a
     * notification has to render now.
     */
    private suspend fun resolveSenderProfile(userId: ID): UserProfile? =
        userProfileDataSource.getCachedProfile(userId)
            ?: profileController.getProfileForUser(userId).getOrNull()
                ?.also { userProfileDataSource.store(userId, it) }

    /** First social handle (e.g. an X username) to render as a display name, if any. */
    private fun UserProfile.socialHandle(): String? =
        socialAccounts.filterIsInstance<SocialAccount.TwitterX>()
            .firstOrNull()
            ?.handle

    /**
     * Loads a remote avatar [url] into a software [Bitmap] via the app's shared
     * Coil [SingletonImageLoader]. Serves from memory/disk cache without a
     * network trip when possible; the network case is bounded by
     * [AVATAR_FETCH_TIMEOUT_MS] so a slow fetch never holds back the
     * notification. Returns `null` on timeout or failure (the notification then
     * posts with the name monogram).
     */
    private suspend fun loadRemoteAvatar(url: String, cacheKey: String?): Bitmap? =
        withTimeoutOrNull(AVATAR_FETCH_TIMEOUT_MS.milliseconds) {
            runCatching {
                val request = ImageRequest.Builder(this@NotificationService)
                    .data(url)
                    // Keyed on the durable blob id like the in-app avatars, so a notification hits
                    // the rendition they already cached instead of re-downloading under a URL that
                    // is different on every mint.
                    .apply {
                        cacheKey?.let { memoryCacheKey(it); diskCacheKey(it) }
                    }
                    .allowHardware(false) // notification icons require a software bitmap
                    .build()
                val result = SingletonImageLoader.get(this@NotificationService).execute(request)
                (result as? SuccessResult)?.image?.toBitmap()
            }.getOrNull()
        }

    private suspend fun resolveContactPhoto(e164: String): Bitmap? {
        val uriString = contactResolver.resolvePhotoUri(e164)
        if (uriString != null) {
            try {
                val source = ImageDecoder.createSource(contentResolver, uriString.toUri())
                return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } catch (e: Exception) {
                trace(tag = "NotificationService", message = "Failed to decode contact photo: ${e.message}", type = TraceType.Log)
            }
        }

        // URI unavailable or decode failed — read directly from contacts provider
        val bytes = contactResolver.resolvePhotoBytes(e164) ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private suspend fun resolveSubstitution(substitution: Substitution): String {
        return when (substitution) {
            is Substitution.Phone -> {
                contactResolver.resolveName(substitution.phoneNumber, substitution.fallback)
            }
            is Substitution.UserId -> {
                resolveUserDisplayName(substitution.userId) ?: substitution.fallback
            }
        }
    }

    /**
     * Resolves [userId] to a display name, cache-first: the normalized `user_profiles` table is
     * fast and works offline (ideal for rendering a push), and we only fall back to a network
     * profile lookup when the user isn't cached. Returns null when neither resolves.
     */
    private suspend fun resolveUserDisplayName(userId: ID): String? =
        userProfileDataSource.getCachedDisplayName(userId)
            ?: profileController.getProfileForUser(userId).getOrNull()
                ?.displayName?.takeIf { it.isNotBlank() }

    private suspend fun applySubstitutions(text: String, substitutions: List<Substitution>): String {
        var result = text
        for ((index, substitution) in substitutions.withIndex()) {
            val resolved = resolveSubstitution(substitution)
            result = result.replace("{$index}", resolved)
        }
        return result
    }

    private fun authenticateIfNeeded(block: () -> Unit) {
        if (userManager.accountCluster == null) {
            authManager.init { block() }
        } else {
            block()
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun buildReplyAction(chatId: ChatId, notificationId: Int): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_TEXT_REPLY)
            .setLabel(getString(R.string.notification_action_reply))
            .build()

        val intent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_REPLY
            putExtra(NotificationActionReceiver.KEY_CHAT_ID_HEX, chatId.bytes.toHexString())
            putExtra(NotificationActionReceiver.KEY_NOTIFICATION_ID, notificationId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            chatId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        return NotificationCompat.Action.Builder(
            R.drawable.ic_reply,
            getString(R.string.notification_action_reply),
            pendingIntent,
        ).addRemoteInput(remoteInput).build()
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun buildMarkAsReadAction(chatId: ChatId): NotificationCompat.Action {
        val intent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_READ
            putExtra(NotificationActionReceiver.KEY_CHAT_ID_HEX, chatId.bytes.toHexString())
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            chatId.hashCode() + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Action.Builder(
            R.drawable.ic_mark_read,
            getString(R.string.notification_action_mark_as_read),
            pendingIntent,
        ).build()
    }

    /**
     * Where this notification's tap goes.
     *
     * @param chatType the type resolved by [planConversationStyling], null for a non-chat push
     */
    internal fun Context.buildContentIntent(
        chatType: ChatType?,
        navigation: NavigationTrigger?,
        ): PendingIntent {
        // The request code travels with the target: `FLAG_UPDATE_CURRENT` rewrites the extras of
        // whichever PendingIntent is already held under it, and `filterEquals` — which decides
        // what "already held" means — ignores extras. Under one shared code every posted
        // notification ends up pointing at whatever was notified last.
        val (target, requestCode) = when (navigation) {
            is NavigationTrigger.CurrencyInfo ->
                Intent(Intent.ACTION_VIEW).apply {
                    data = Linkify.tokenInfo(navigation.mint).toUri()
                } to navigation.mint.hashCode()

            is NavigationTrigger.Chat.ById -> {
                val intent = when (val target = planChatTapTarget(chatType, navigation.chatId)) {
                    is ChatTapTarget.Conversation -> Intent(Intent.ACTION_VIEW).apply {
                        data = target.link.toUri()
                    }

                    ChatTapTarget.AppLauncher -> packageManager.getLaunchIntentForPackage(packageName)
                }
                // The same code the reply action uses, which is not a collision: a PendingIntent
                // is identified by its type too, and that one is a broadcast.
                intent to navigation.chatId.hashCode()
            }

            // Every one of these is the same launcher intent, so they can share a code.
            else -> packageManager.getLaunchIntentForPackage(packageName) to LAUNCH_REQUEST_CODE
        }

        return PendingIntent.getActivity(
            this,
            requestCode,
            target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}