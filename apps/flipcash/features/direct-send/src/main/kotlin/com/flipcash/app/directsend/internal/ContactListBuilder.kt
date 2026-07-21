package com.flipcash.app.directsend.internal

import com.flipcash.app.contacts.ContactCoordinator.ContactState
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.app.phone.PhoneUtils
import com.flipcash.features.directsend.R
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatSummary
import com.flipcash.shared.chat.ui.ConversationReference
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ResourceHelper
import javax.inject.Inject

/**
 * Builds the send-flow contact list from the raw inputs: device contacts, the
 * chat feed, the current search string, and known tokens.
 *
 * Pure and free of the ViewModel/flow machinery so it can be unit-tested
 * directly. Ephemeral overlays (e.g. typing indicators) are applied on top of
 * this output separately — see [SendFlowViewModel] — so they don't force a
 * re-filter/re-sort of the whole list.
 */
internal class ContactListBuilder @Inject constructor(
    private val userManager: UserManager,
    private val phoneUtils: PhoneUtils,
    private val resources: ResourceHelper,
) {

    fun build(
        contactState: ContactState,
        searchString: String,
        chatFeed: List<ChatSummary>,
        tokensByMint: Map<Mint, Token>,
    ): List<ContactListItem> = buildList {
        val selfId = userManager.accountId
        val selfPhone = userManager.profile?.verifiedPhoneNumber

        val allContacts = contactState.contacts.values
            .filter { selfPhone == null || it.e164 != selfPhone }
        val filtered = if (searchString.isBlank()) {
            allContacts
        } else {
            allContacts.filter {
                it.displayName.contains(searchString, ignoreCase = true) ||
                        it.e164.contains(searchString, ignoreCase = true)
            }
        }

        val dmChats = chatFeed.filter { it.metadata.type == ChatType.CONTACT_DM }
        // Build a reverse lookup: e164 -> chatId string for contacts with DMs
        val e164ToChatId = contactState.dmChatIds

        // Recents — driven by the chat feed, enriched with contact info
        val recentsE164s = mutableSetOf<String>()
        val recentRows = dmChats.mapNotNull { summary ->
            val chatId = summary.metadata.chatId
            val chatIdStr = chatId.toString()

            // Try to match this chat to a device contact
            val e164 = e164ToChatId.entries
                .firstOrNull { it.value == chatIdStr }?.key
            val deviceContact = e164
                ?.takeIf { selfPhone == null || it != selfPhone }
                ?.let { contactState.contacts[it] }

            val contact = if (deviceContact != null) {
                if (searchString.isNotBlank() &&
                    !deviceContact.displayName.contains(searchString, ignoreCase = true) &&
                    !deviceContact.e164.contains(searchString, ignoreCase = true)
                ) {
                    return@mapNotNull null
                }
                recentsE164s += deviceContact.e164
                deviceContact
            } else {
                // Non-contact DM — build contact from chat member profile
                val isSelf = { member: ChatMember ->
                    member.userId == selfId || (selfPhone != null && member.userProfile.verifiedPhoneNumber == selfPhone)
                }
                val otherMember = summary.metadata.members
                    .firstOrNull { !isSelf(it) } ?: return@mapNotNull null
                val phone = otherMember.userProfile.verifiedPhoneNumber
                val formattedPhone = phone?.let { phoneUtils.formatNumber(it) }
                val displayName = otherMember.userProfile.displayName?.takeIf { it.isNotBlank() }
                    ?: formattedPhone
                    ?: return@mapNotNull null

                val unknown = DeviceContact.unknownContact(
                    e164 = phone.orEmpty(),
                    displayName = displayName,
                    displayNumber = formattedPhone,
                )
                if (searchString.isNotBlank() &&
                    !unknown.displayName.contains(searchString, ignoreCase = true)
                ) {
                    return@mapNotNull null
                }
                if (unknown.e164.isNotEmpty()) {
                    recentsE164s += unknown.e164
                }
                unknown
            }

            ContactListItem.ContactRow(
                contact = contact,
                isOnFlipcash = true,
                lastActivity = summary.metadata.lastActivity,
                conversation = ConversationReference(
                    chatId = chatId,
                    lastMessagePreview = formatPreview(summary, selfId, tokensByMint),
                    unreadCount = summary.unreadCount,
                ),
            )
        }

        // On Flipcash — contacts that haven't chatted yet, use joinedAt as their sort timestamp
        val flipcashRows = filtered
            .filter { it.e164 in contactState.flipcashE164s && it.e164 !in recentsE164s }
            .map { contact ->
                ContactListItem.ContactRow(
                    contact = contact,
                    isOnFlipcash = true,
                    lastActivity = contactState.joinedAtByE164[contact.e164],
                )
            }

        val flipcashCombined = (recentRows + flipcashRows).sortedWith(
            compareByDescending<ContactListItem.ContactRow> { it.lastActivity }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.contact.displayName }
        )

        val excludedE164s = recentsE164s + contactState.flipcashE164s
        val other = filtered
            .filter { it.e164 !in excludedE164s }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })

        addAll(flipcashCombined)
        if (other.isNotEmpty()) {
            add(ContactListItem.Header(resources.getString(R.string.title_nonFlipcashContacts)))
            other.forEach { add(ContactListItem.ContactRow(it, isOnFlipcash = false)) }
        }
    }

    private fun formatPreview(
        summary: ChatSummary,
        selfId: ID?,
        tokensByMint: Map<Mint, Token>,
    ): String? {
        val lastMsg = summary.metadata.lastMessage ?: return null
        val sentBySelf = lastMsg.senderId != null && lastMsg.senderId == selfId
        return lastMsg.content.firstOrNull()?.let { content ->
            when (content) {
                is MessageContent.Text -> {
                    val message = content.text.takeIf { it.isNotEmpty() } ?: return null
                    if (sentBySelf) {
                        resources.getString(R.string.label_chat_preview_sentMessage, message)
                    } else {
                        message
                    }
                }
                is MessageContent.Cash -> {
                    val formatted = content.amount.formatted()
                    val name = content.tokenName.ifBlank { tokensByMint[content.mint]?.name.orEmpty() }
                    val label = if (name.isNotBlank()) {
                        resources.getString(R.string.label_chat_preview_cash_suffix, formatted, name)
                    } else {
                        formatted
                    }
                    if (sentBySelf) {
                        resources.getString(R.string.label_chat_preview_sentCash, label)
                    } else {
                        resources.getString(R.string.label_chat_preview_receivedCash, label)
                    }
                }

                // TODO:
                is MessageContent.Deleted -> null
                is MessageContent.Media -> null
                is MessageContent.Reply -> null
                is MessageContent.System -> null
            }
        }
    }
}
