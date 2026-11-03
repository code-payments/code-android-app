package com.flipcash.shared.chat.models

import com.flipcash.core.resources.Res
import com.flipcash.core.resources.label_chat_system_added
import com.flipcash.core.resources.label_chat_system_addedSelf
import com.flipcash.core.resources.label_chat_system_joined
import com.flipcash.core.resources.label_chat_system_joinedSelf
import com.flipcash.core.resources.label_chat_system_joinedSelfViaInvite
import com.flipcash.core.resources.label_chat_system_joinedViaInvite
import com.flipcash.core.resources.label_chat_system_joinedViaYourInvite
import com.flipcash.core.resources.label_chat_system_selfAdded
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.isSystemBlank
import com.getcode.opencode.model.core.ID
import com.getcode.util.resources.ResourceHelper

/**
 * The text a system notice draws and previews as, or `null` when it has none.
 *
 * The one place the notice's wording is chosen. A [MessageContent.System.event] this client knows
 * is worded here, naming its users through [nameOf] and the viewer ([selfId]) as "you". When there
 * is no event, or [nameOf] can't name someone it needs yet, the notice reads as its fallback text.
 *
 * Blank is [isSystemBlank], the definition iOS shares. Fallback text that is not blank is returned
 * as it arrived: not trimmed. Rows stored before the mapper dropped blank notices still reach this.
 */
fun MessageContent.System.displayText(
    resources: ResourceHelper,
    selfId: ID?,
    nameOf: (ID) -> String?,
): String? = event?.worded(resources, selfId, nameOf)
    ?: fallbackText.takeUnless { it.isSystemBlank() }

private fun MessageContent.SystemEvent.worded(
    resources: ResourceHelper,
    selfId: ID?,
    nameOf: (ID) -> String?,
): String? = when (this) {
    is MessageContent.SystemEvent.UserJoined -> {
        val joinedIsSelf = selfId != null && userId == selfId
        val admitter = admittedBy?.takeUnless { it == userId }
        val inviter = invitedBy
        when {
            // Joined with someone's invite link (option B). Before the admitted forms: a join is one
            // or the other, and the inviter is who the link belongs to.
            inviter != null -> when {
                joinedIsSelf -> nameOf(inviter)
                    ?.let { resources.getString(Res.string.label_chat_system_joinedSelfViaInvite, it) }
                selfId != null && inviter == selfId -> nameOf(userId)
                    ?.let { resources.getString(Res.string.label_chat_system_joinedViaYourInvite, it) }
                else -> {
                    val joinedName = nameOf(userId)
                    val inviterName = nameOf(inviter)
                    if (joinedName != null && inviterName != null) {
                        resources.getString(Res.string.label_chat_system_joinedViaInvite, joinedName, inviterName)
                    } else {
                        null
                    }
                }
            }
            admitter == null -> if (joinedIsSelf) {
                resources.getString(Res.string.label_chat_system_joinedSelf)
            } else {
                nameOf(userId)?.let { resources.getString(Res.string.label_chat_system_joined, it) }
            }
            selfId != null && admitter == selfId ->
                nameOf(userId)?.let { resources.getString(Res.string.label_chat_system_selfAdded, it) }
            joinedIsSelf ->
                nameOf(admitter)?.let { resources.getString(Res.string.label_chat_system_addedSelf, it) }
            else -> {
                val admitterName = nameOf(admitter)
                val joinedName = nameOf(userId)
                if (admitterName != null && joinedName != null) {
                    resources.getString(Res.string.label_chat_system_added, admitterName, joinedName)
                } else {
                    null
                }
            }
        }
    }
}

/** The users [MessageContent.System.event] names, apart from the viewer: the ones [displayText] looks up. */
fun MessageContent.System.namedUsers(selfId: ID?): List<ID> = when (val event = event) {
    is MessageContent.SystemEvent.UserJoined -> listOfNotNull(event.userId, event.admittedBy, event.invitedBy)
    null -> emptyList()
}.distinct().filterNot { selfId != null && it == selfId }

/** A name in a notice's [text][ChatListItem.SystemMessage.text] that opens [userId]'s profile. */
data class SystemUserLink(val start: Int, val end: Int, val userId: ID)

/**
 * The tappable names in [text], the result of [displayText] for this notice. Only the inviter of an
 * invite-link join is one: "your" and "you" name the viewer, who has no profile to open from here.
 *
 * The span is found by formatting the same string again with a marker in the inviter's place, so
 * where the name sits follows the string resource and not an assumption about its wording. If the
 * result doesn't line up with [text], there is no link.
 */
fun MessageContent.System.userLinks(
    resources: ResourceHelper,
    text: String,
    selfId: ID?,
    nameOf: (ID) -> String?,
): List<SystemUserLink> {
    val joined = event as? MessageContent.SystemEvent.UserJoined ?: return emptyList()
    val inviter = joined.invitedBy?.takeUnless { selfId != null && it == selfId } ?: return emptyList()
    val name = nameOf(inviter) ?: return emptyList()
    val marked = when {
        selfId != null && joined.userId == selfId ->
            resources.getString(Res.string.label_chat_system_joinedSelfViaInvite, INVITER_MARKER)
        else -> {
            val joinedName = nameOf(joined.userId) ?: return emptyList()
            resources.getString(Res.string.label_chat_system_joinedViaInvite, joinedName, INVITER_MARKER)
        }
    }
    val at = marked.indexOf(INVITER_MARKER)
    if (at < 0 || !text.regionMatches(at, name, 0, name.length)) return emptyList()
    return listOf(SystemUserLink(at, at + name.length, inviter))
}

// A private-use character, so it can't collide with a name or the surrounding wording.
private const val INVITER_MARKER = "\uE000"
