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
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.contacts.ContactResolver
import com.flipcash.app.core.util.Linkify
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.PushController
import com.flipcash.services.models.SocialAccount
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.NavigationTrigger
import com.flipcash.services.models.NotificationCategory
import com.flipcash.services.models.NotificationPayload
import com.flipcash.services.models.Substitution
import com.flipcash.services.user.UserManager
import com.flipcash.shared.notifications.R
import com.getcode.utils.TraceType
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
    lateinit var notificationManager: NotificationManagerCompat

    @Inject
    lateinit var tokenCoordinator: TokenCoordinator

    @Inject
    lateinit var contactCoordinator: ContactCoordinator

    @Inject
    lateinit var contactResolver: ContactResolver

    @Inject
    lateinit var chatCoordinator: ChatCoordinator

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

        trace(
            message = "onMessageReceived",
            type = TraceType.Process,
            metadata = {
                "title" to title
                "body" to body
            }
        )

        if (title == null) return

        val payload = message.data.getOrDefault(KEY_PAYLOAD, "")
            .takeIf { it.isNotEmpty() }
            ?.let { NotificationPayload.fromEncoded(it) }

        if (payload?.navigation is NavigationTrigger.CurrencyInfo) {
            launch { tokenCoordinator.update() }
        }

        if (payload?.category == NotificationCategory.CONTACT_JOIN) {
            launch {
                chatCoordinator.refreshFeed()
            }
        }

        when (val trigger = payload?.navigation) {
            is NavigationTrigger.Chat.ById -> {
                launch {
                    chatCoordinator.refreshFeed()
                    chatCoordinator.loadMessages(chatId = trigger.chatId)
                }
            }
            else -> Unit
        }

        authenticateIfNeeded {
            launch {
                try {
                    if (payload?.category == NotificationCategory.CONTACT_JOIN) {
                        launch { contactCoordinator.sync() }
                    }
                    val resolvedTitle = applySubstitutions(title, payload?.titleSubstitutions.orEmpty())
                    val resolvedBody = body?.let { applySubstitutions(it, payload?.bodySubstitutions.orEmpty()) }
                    postNotification(resolvedTitle, resolvedBody, payload)
                } catch (e: Exception) {
                    trace(tag = "NotificationService", message = "Failed to post notification", error = e)
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

        val groupKey = payload?.groupKey?.takeIf { it.isNotEmpty() }

        val builder = NotificationCompat.Builder(this, channel.id)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setSmallIcon(R.drawable.flipcash_logo)
            .setColor(getColor(R.color.notification_color))
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(payload?.navigation))
            .apply {
                if (groupKey != null) setGroup(groupKey)
            }

        val notificationId = if (chatId != null) {
            builder.applyChatStyle(chatId, groupKey, title, body)
        } else {
            builder.setContentTitle(title).setContentText(body)
            SecureRandom().nextInt(Int.MAX_VALUE)
        }

        notificationManager.notify(notificationId, builder.build())

        if (groupKey != null) {
            val summary = NotificationCompat.Builder(this, channel.id)
                .setSmallIcon(R.drawable.flipcash_logo)
                .setColor(getColor(R.color.notification_color))
                .setGroup(groupKey)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(groupKey.hashCode(), summary)
        }
    }

    private suspend fun NotificationCompat.Builder.applyChatStyle(
        chatId: ChatId,
        groupKey: String?,
        title: String?,
        body: String?,
    ): Int {
        val notificationId = chatId.hashCode()

        // Prefer the device-contact identity (CONTACT_DM, or a counterparty saved
        // in the address book): the user's own name + photo for them. Only when
        // that's absent do we fetch the chat member and fall back to their
        // server-side profile — which is the only identity a TIP_DM has.
        val contactE164 = contactCoordinator.lookupContactByDmChatId(chatId.toString())?.e164
        val member = if (contactE164 == null) chatCoordinator.getOtherMember(chatId) else null
        val e164 = contactE164 ?: member?.userProfile?.verifiedPhoneNumber

        val senderName = e164?.let { contactResolver.resolveName(it) }
            ?: member?.userProfile?.displayName?.takeIf { it.isNotBlank() }
            ?: member?.userProfile?.socialHandle()
            ?: title
            ?: ""

        // Device-contact photo (local, synchronous) first; otherwise the profile
        // picture URL loaded through the app's shared Coil loader (cache-first,
        // network-bounded). Works for CONTACT_DM and TIP_DM alike.
        //
        // Size the rendition to the platform's large-icon dimension (density-scaled) rather than
        // grabbing the smallest THUMBNAIL — the server ships several thumbnail/display sizes and
        // the tiny 32px one looks grainy on the notification's person icon.
        val avatarPx = resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
        val avatar = e164?.let { resolveContactPhoto(it) }
            ?: member?.userProfile?.profilePicture
                ?.urlForSize(avatarPx)
                ?.let { loadRemoteAvatar(it) }

        trace(
            tag = "NotificationService",
            message = "applyChatStyle: chatId=$chatId, groupKey=$groupKey, e164=$e164, hasMember=${member != null}, hasAvatar=${avatar != null}, authenticated=${userManager.accountCluster != null}",
            type = TraceType.Log,
        )

        val selfPerson = buildSelfPerson(this@NotificationService, userManager.profile, contactResolver)

        val senderPerson = Person.Builder()
            .setName(senderName)
            .setKey(groupKey ?: "unknown")
            .apply {
                if (avatar != null) setIcon(IconCompat.createWithBitmap(avatar.toCircularBitmap()))
            }
            .build()

        val style = notificationManager.activeNotifications
            .firstOrNull { it.id == notificationId }
            ?.notification
            ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
            ?: NotificationCompat.MessagingStyle(selfPerson)

        style.addMessage(body.orEmpty(), System.currentTimeMillis(), senderPerson)

        setStyle(style)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .addAction(buildReplyAction(chatId, notificationId, groupKey))
            .addAction(buildMarkAsReadAction(chatId, groupKey))

        return notificationId
    }

    /** First social handle (e.g. an X username) to render as a display name, if any. */
    private fun UserProfile.socialHandle(): String? =
        socialAccounts.filterIsInstance<SocialAccount.TwitterX>()
            .firstOrNull()
            ?.username
            ?.let { "@$it" }

    /**
     * Loads a remote avatar [url] into a software [Bitmap] via the app's shared
     * Coil [SingletonImageLoader]. Serves from memory/disk cache without a
     * network trip when possible; the network case is bounded by
     * [AVATAR_FETCH_TIMEOUT_MS] so a slow fetch never holds back the
     * notification. Returns `null` on timeout or failure (the notification then
     * posts with the name monogram).
     */
    private suspend fun loadRemoteAvatar(url: String): Bitmap? =
        withTimeoutOrNull(AVATAR_FETCH_TIMEOUT_MS.milliseconds) {
            runCatching {
                val request = ImageRequest.Builder(this@NotificationService)
                    .data(url)
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
        }
    }

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
    private fun buildReplyAction(chatId: ChatId, notificationId: Int, groupKey: String?): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_TEXT_REPLY)
            .setLabel(getString(R.string.notification_action_reply))
            .build()

        val intent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_REPLY
            putExtra(NotificationActionReceiver.KEY_CHAT_ID_HEX, chatId.bytes.toHexString())
            putExtra(NotificationActionReceiver.KEY_NOTIFICATION_ID, notificationId)
            if (groupKey != null) putExtra(NotificationActionReceiver.KEY_GROUP_KEY, groupKey)
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
    private fun buildMarkAsReadAction(chatId: ChatId, groupKey: String?): NotificationCompat.Action {
        val intent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_READ
            putExtra(NotificationActionReceiver.KEY_CHAT_ID_HEX, chatId.bytes.toHexString())
            if (groupKey != null) putExtra(NotificationActionReceiver.KEY_GROUP_KEY, groupKey)
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

    internal fun Context.buildContentIntent(navigation: NavigationTrigger?): PendingIntent {
        val target = when (navigation) {
            is NavigationTrigger.CurrencyInfo -> Intent(Intent.ACTION_VIEW).apply {
                data = Linkify.tokenInfo(navigation.mint).toUri()
            }

            is NavigationTrigger.Chat.ById -> Intent(Intent.ACTION_VIEW).apply {
                data = Linkify.chatById(navigation.chatId).toUri()
            }

            is NavigationTrigger.Chat.ByContact -> Intent(Intent.ACTION_VIEW).apply {
                data = Linkify.chatByPhone(navigation.phoneNumber).toUri()
            }

            else -> packageManager.getLaunchIntentForPackage(packageName)
        }

        return PendingIntent.getActivity(
            this,
            99,
            target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}