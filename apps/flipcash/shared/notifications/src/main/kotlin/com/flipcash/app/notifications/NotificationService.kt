package com.flipcash.app.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.media.RingtoneManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.contacts.ContactResolver
import com.flipcash.app.core.util.Linkify
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.PushController
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
import java.security.SecureRandom
import javax.inject.Inject

@AndroidEntryPoint
class NotificationService : FirebaseMessagingService(),
    CoroutineScope by CoroutineScope(Dispatchers.IO) {

    companion object {
        private const val KEY_TITLE = "push_notification_title"
        private const val KEY_BODY = "push_notification_body"
        private const val KEY_PAYLOAD = "flipcash_payload"
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

    private suspend fun postNotification(title: String, body: String?, payload: NotificationPayload?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val category = payload?.category ?: NotificationCategory.DEFAULT
        NotificationChannels.ensureChannelGroups(this, notificationManager)
        val channel = NotificationChannels.channelFor(this, category)
        notificationManager.createNotificationChannel(channel)

        val chatId = (payload?.navigation as? NavigationTrigger.Chat)?.chatId
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
            builder.applyContactChatStyle(chatId.hashCode(), groupKey, body)
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

    private suspend fun NotificationCompat.Builder.applyContactChatStyle(
        notificationId: Int,
        groupKey: String?,
        body: String?,
    ): Int {
        val e164 = groupKey?.takeIf { it.startsWith("+") }
        val contactPhoto = e164?.let { resolveContactPhoto(it) }
        val senderName = e164?.let { contactResolver.resolveName(it) } ?: ""

        val profile = userManager.profile
        val selfPerson = Person.Builder()
            .setName(
                profile?.displayName?.takeIf { it.isNotEmpty() }
                    ?: profile?.verifiedPhoneNumber?.takeIf { it.isNotEmpty() }
                    ?: getString(R.string.notification_self_person_name)
            )
            .setKey("self")
            .build()

        val senderPerson = Person.Builder()
            .setName(senderName)
            .setKey(groupKey ?: "unknown")
            .apply {
                if (contactPhoto != null) setIcon(IconCompat.createWithBitmap(contactPhoto.toCircularBitmap()))
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

        return notificationId
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

    private fun Bitmap.toCircularBitmap(): Bitmap {
        val size = minOf(width, height)
        val xOffset = (width - size) / 2
        val yOffset = (height - size) / 2

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val half = size / 2f
        canvas.drawCircle(half, half, half, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(this, -xOffset.toFloat(), -yOffset.toFloat(), paint)

        return output
    }

    internal fun Context.buildContentIntent(navigation: NavigationTrigger?): PendingIntent {
        val target = when (navigation) {
            is NavigationTrigger.CurrencyInfo -> Intent(Intent.ACTION_VIEW).apply {
                data = Linkify.tokenInfo(navigation.mint).toUri()
            }

            is NavigationTrigger.Chat -> Intent(Intent.ACTION_VIEW).apply {
                data = Linkify.chat(navigation.chatId).toUri()
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