package com.flipcash.app.messenger.internal

import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.ChatRules
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.shared.chat.models.LinkCard

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

    /** A `TIP_DM`. Blocking is reached through its profile. */
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
     * splitting them would let the screen blur a chat it had just joined for one frame. It is null
     * for a group hydrated by id, which cannot know it — see `ChatMembership.isMember`. What the
     * viewer may see treats null as a non-member; what the gate's button offers does not.
     */
    data class Group(
        val chatId: ChatId,
        val groupTitle: String?,
        val picture: MediaItem?,
        val memberCount: Long,
        val rules: ChatRules?,
        val isMember: Boolean?,
    ) : ChatSubject {
        override val title: String get() = groupTitle.orEmpty()
        override val subtitle: String? get() = null
        // The group's own profile, not a counterparty's — which is why this is true while
        // [asParticipant] stays null. The route the tap takes is decided per arm, in
        // MessengerScreen's ChatAction.ViewProfile handler.
        override val canViewProfile: Boolean get() = true
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
 * [ChatRuleRequirement.Staff] is not a balance and has no amount to state — [requiresStaff] answers
 * for it separately, because the two can both be set on one chat.
 */
internal fun ChatRules?.balanceRequirement(): ChatRuleRequirement.MinimumBalance? {
    val rules = this ?: return null
    return rules.listener.filterIsInstance<ChatRuleRequirement.MinimumBalance>().firstOrNull()
        ?: rules.speaker.filterIsInstance<ChatRuleRequirement.MinimumBalance>().firstOrNull()
}

/**
 * Whether the chat is open to Flipcash staff only.
 *
 * Worth stating even though it is not a bar the user can go and clear: it is the reason the button
 * is dead, and a disabled Join over no explanation is the screen saying nothing. Staff read the same
 * line over a Join that works, which is how the balance requirement is already handled.
 *
 * Either list counts, unlike [balanceRequirement], which picks one requirement to state: this
 * answers yes or no, so there is nothing to prefer between them.
 */
internal fun ChatRules?.requiresStaff(): Boolean {
    val rules = this ?: return false
    return ChatRuleRequirement.Staff in rules.listener ||
        ChatRuleRequirement.Staff in rules.speaker
}

/**
 * How the token behind a balance requirement is written on screen.
 *
 * Two answers rather than one, because the reserve is named in a button but not in a sentence: "Buy
 * More Dollars" is what the user taps, while "Minimum Balance: $100 of Dollars" says dollars twice —
 * the amount is already a dollar figure. Every other token is named in both places, since "$100"
 * alone would not say which holding clears the bar.
 */
internal data class RuleCurrency(
    /** What the user calls this token — [com.flipcash.app.core.tokens.brandedName]. */
    val name: String,
    /** The reserve, which an amount in dollars has already named. */
    val isReserve: Boolean,
) {
    /** The token to name in the requirement line, or `null` to state the amount alone. */
    val nameInRequirement: String?
        get() = name.takeUnless { isReserve }
}

/**
 * The card an empty group shows in place of its info card: the same [LinkCard.GroupInvite] a
 * transcript renders for an invite link, built from what this subject already knows rather than
 * from a lookup, since a member looking at the empty group already has the chat's own record.
 */
internal fun ChatSubject.Group.toGroupInviteCard(
    inviteUrl: String,
    currencyName: String?,
): LinkCard.GroupInvite {
    val balance = rules.balanceRequirement()
    val staffOnly = rules.requiresStaff()
    val requirement = if (balance != null || staffOnly) {
        LinkCard.GroupInvite.Requirement(
            amount = balance?.amount?.formatted(),
            currencyName = currencyName,
            staffOnly = staffOnly,
        )
    } else {
        null
    }
    return LinkCard.GroupInvite(
        url = inviteUrl,
        start = 0,
        end = inviteUrl.length,
        chatId = chatId,
        state = LinkCard.GroupInvite.State.Resolved(
            title = groupTitle?.takeIf { it.isNotBlank() },
            picture = picture,
            memberCount = memberCount,
            requirement = requirement,
        ),
    )
}
