package com.flipcash.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ReactionUpdate
import com.flipcash.shared.chat.alerts.ReactionActorProfiles
import com.flipcash.shared.chat.alerts.ReactionAlertController
import com.flipcash.shared.chat.alerts.ReactionAlertEvent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Debug builds only: fires canned in-app reaction alerts so the banner can be driven from adb.
 *
 *     adb shell am broadcast -a com.flipcash.debug.REACTION_ALERT --es kind reaction
 *     adb shell am broadcast -a com.flipcash.debug.REACTION_ALERT --es kind boost
 *     adb shell am broadcast -a com.flipcash.debug.REACTION_ALERT --es kind stack
 *
 * The reactor is the first known chat member whose name is `--es name` (default "XCTests"), and the
 * photo comes through the same lookup the real stream uses. `stack` sends four reactors one after
 * another so the card grows from 1 to 4 people.
 */
class ReactionAlertDebugReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun controller(): ReactionAlertController
        fun members(): ChatMemberDataSource
        fun profiles(): ReactionActorProfiles
    }

    override fun onReceive(context: Context, intent: Intent) {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        val controller = deps.controller()
        val members = deps.members()
        val profiles = deps.profiles()
        val kind = intent.getStringExtra("kind") ?: "reaction"
        val name = intent.getStringExtra("name") ?: "XCTests"
        val pending = goAsync()
        scope.launch {
            try {
                fire(kind, name, controller, members, profiles)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(
        kind: String,
        name: String,
        controller: ReactionAlertController,
        members: ChatMemberDataSource,
        profiles: ReactionActorProfiles,
    ) {
        val all = members.observeAll().first()
        val (chatHex, member) = all.entries.firstNotNullOfOrNull { (hex, list) ->
            list.firstOrNull { it.userProfile.displayName == name }?.let { hex to it }
        } ?: all.entries.firstNotNullOf { (hex, list) -> list.firstOrNull()?.let { hex to it } }
        val chat = ChatId(chatHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        val resolved = profiles.resolve(member.userId)
        val actorName = resolved?.displayName?.takeIf { it.isNotBlank() } ?: member.userProfile.displayName
        val photo = member.userProfile.profilePicture ?: resolved?.profilePicture

        fun event(who: List<Byte>, whoName: String, whoPhoto: Boolean, emoji: String, action: ReactionUpdate.Action, cents: Long? = null) =
            ReactionAlertEvent(
                chatId = chat,
                messageId = 1,
                emoji = emoji,
                actor = who,
                action = action,
                boostDeltaCents = cents,
                actorName = whoName,
                chatName = "Moony",
                quote = "Me! I'll send my \$40 tonight",
                actorPhoto = if (whoPhoto) photo else null,
            )

        when (kind) {
            "boost" -> controller.offer(event(member.userId, actorName, true, "🔥", ReactionUpdate.Action.BOOSTED, 500))
            "stack" -> {
                val crowd = listOf("Chloe" to "❤️", "Sam" to "😂", "Jo" to "👏")
                controller.offer(event(member.userId, actorName, true, "🔥", ReactionUpdate.Action.ADDED))
                crowd.forEachIndexed { i, (n, e) ->
                    delay(900)
                    controller.offer(event(listOf(i.toByte(), 9), n, false, e, ReactionUpdate.Action.ADDED))
                }
            }
            else -> controller.offer(event(member.userId, actorName, true, "🔥", ReactionUpdate.Action.ADDED))
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
