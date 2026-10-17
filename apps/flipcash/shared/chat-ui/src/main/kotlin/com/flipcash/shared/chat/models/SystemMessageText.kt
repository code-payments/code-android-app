package com.flipcash.shared.chat.models

import com.flipcash.core.R
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
        when {
            admitter == null -> if (joinedIsSelf) {
                resources.getString(R.string.label_chat_system_joinedSelf)
            } else {
                nameOf(userId)?.let { resources.getString(R.string.label_chat_system_joined, it) }
            }
            selfId != null && admitter == selfId ->
                nameOf(userId)?.let { resources.getString(R.string.label_chat_system_selfAdded, it) }
            joinedIsSelf ->
                nameOf(admitter)?.let { resources.getString(R.string.label_chat_system_addedSelf, it) }
            else -> {
                val admitterName = nameOf(admitter)
                val joinedName = nameOf(userId)
                if (admitterName != null && joinedName != null) {
                    resources.getString(R.string.label_chat_system_added, admitterName, joinedName)
                } else {
                    null
                }
            }
        }
    }
}

/** The users [MessageContent.System.event] names, apart from the viewer: the ones [displayText] looks up. */
fun MessageContent.System.namedUsers(selfId: ID?): List<ID> = when (val event = event) {
    is MessageContent.SystemEvent.UserJoined -> listOfNotNull(event.userId, event.admittedBy)
    null -> emptyList()
}.distinct().filterNot { selfId != null && it == selfId }
