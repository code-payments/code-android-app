package com.flipcash.app.messenger.internal

import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.ChatRules
import com.flipcash.services.models.chat.MediaItem

/**
 * What the messenger screen is a conversation *with*.
 *
 * [ChatParticipant] answers this for a DM and is unchanged — [Contact] and [TipUser] wrap it
 * rather than restating it, so DM rendering keeps reading the same fields off the same type. What
 * the subject adds is a [Group] arm and, with it, exhaustiveness: the title bar, the info card and
 * the profile route each become a `when` over three arms, so a fourth chat type is a compile error
 * at every site that has to decide something rather than a blank render at one of them.
 *
 * Deliberately not `Parcelable`. Nothing bundles a subject —
 * [com.flipcash.app.messenger.internal.screens.ChatStep.Profile] takes a [ChatParticipant], which
 * is why [asParticipant] exists — and parcelling one would mean parcelling [ChatRules].
 */
sealed interface ChatSubject {

    /** What to call this conversation. Never null; a nameless subject reads as empty, not absent. */
    val title: String

    /**
     * The line under the title, or `null` when there is none.
     *
     * [Group] answers `null` even though it has one to show: "3 people" is a plural resource and
     * resolving it needs a `Context` this type has no business holding. The title bar renders it
     * from [Group.memberCount] instead.
     */
    val subtitle: String?

    /** Whether tapping the title or the info card's chevron opens a profile. */
    val canViewProfile: Boolean

    /**
     * The [ChatParticipant] behind this subject, or `null` for a [Group].
     *
     * The bridge to code that predates the subject: the profile route, the tip recipient flow and
     * the payment paths all still speak `ChatParticipant`, and none of them can be reached from a
     * group.
     */
    fun asParticipant(): ChatParticipant?

    /**
     * A `CONTACT_DM`. Carried, not extended: contact DMs are end-of-life, and this arm exists so
     * their phone line and add-to-contacts pill keep rendering exactly as they did.
     */
    data class Contact(val participant: ChatParticipant.Contact) : ChatSubject {
        override val title: String get() = participant.name.orEmpty()
        override val subtitle: String? get() = null
        override val canViewProfile: Boolean get() = false
        override fun asParticipant(): ChatParticipant = participant
    }

    /** A `TIP_DM`. The only arm with a profile to open — blocking is reached through it. */
    data class TipUser(val participant: ChatParticipant.TipUser) : ChatSubject {
        override val title: String get() = participant.name.orEmpty()
        // `handle` already carries the leading `@` (see `String.asHandle`), so this is the raw
        // value rather than one this type prefixes again.
        override val subtitle: String? get() = participant.handle
        override val canViewProfile: Boolean get() = true
        override fun asParticipant(): ChatParticipant = participant
    }

    /**
     * A `GROUP`. Everything here comes off the chat's own row rather than off a counterparty,
     * because a group has none.
     *
     * [isMember] rides along because the access gate reads it in the same breath as [rules], and
     * splitting them would let the screen blur a chat it had just joined for one frame.
     */
    data class Group(
        val chatId: ChatId,
        val groupTitle: String?,
        val picture: MediaItem?,
        val memberCount: Long,
        val rules: ChatRules?,
        val isMember: Boolean,
    ) : ChatSubject {
        override val title: String get() = groupTitle.orEmpty()
        override val subtitle: String? get() = null
        override val canViewProfile: Boolean get() = false
        override fun asParticipant(): ChatParticipant? = null
    }
}

/**
 * The subject for a DM counterparty.
 *
 * The bridge in the other direction from [ChatSubject.asParticipant]: the profile route still
 * carries a [ChatParticipant] through navigation, and its header renders the same avatar the
 * conversation bar does. A group has no participant to arrive here with, so `null` in is `null` out.
 */
internal fun ChatParticipant?.asSubject(): ChatSubject? = when (this) {
    is ChatParticipant.Contact -> ChatSubject.Contact(this)
    is ChatParticipant.TipUser -> ChatSubject.TipUser(this)
    null -> null
}

/**
 * The balance requirement to show the user, or `null` when there is nothing actionable to show.
 *
 * Listener rules gate reading and joining, speaker rules gate sending, and speaker rules apply on
 * top of listener rules — so the first thing standing between the viewer and the chat is the
 * listener requirement when there is one.
 *
 * [ChatRuleRequirement.Staff] deliberately produces nothing: it is not a bar a user can clear by
 * doing something, and rendering it as a requirement would read as an instruction.
 */
internal fun ChatRules?.balanceRequirement(): ChatRuleRequirement.MinimumBalance? {
    val rules = this ?: return null
    return rules.listener.filterIsInstance<ChatRuleRequirement.MinimumBalance>().firstOrNull()
        ?: rules.speaker.filterIsInstance<ChatRuleRequirement.MinimumBalance>().firstOrNull()
}
