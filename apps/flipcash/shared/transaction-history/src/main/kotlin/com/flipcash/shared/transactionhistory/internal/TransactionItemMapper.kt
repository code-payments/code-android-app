package com.flipcash.shared.transactionhistory.internal

import com.flipcash.app.core.feed.ActivityFeedMessageWithToken
import com.flipcash.app.core.feed.MessageMetadata
import com.flipcash.app.core.feed.MessageSubstitution
import com.flipcash.services.models.UserProfile
import com.flipcash.shared.transactionhistory.R
import com.flipcash.shared.transactionhistory.TransactionAvatar
import com.flipcash.shared.transactionhistory.TransactionListItem
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.core.ID
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.hexEncodedString
import javax.inject.Inject

/**
 * Maps a feed item to a presentation item, resolving the counterparty avatar and title from the
 * observed [profiles] cache (keyed by user-id hex). Pure and synchronous — no I/O — so it is safe
 * inside the paging transform; profiles arrive reactively as the cache is observed, so a
 * not-yet-cached counterparty simply resolves later when its profile lands.
 */
internal class TransactionItemMapper @Inject constructor(
    private val resources: ResourceHelper,
): Mapper<Pair<ActivityFeedMessageWithToken, Map<String, UserProfile>>, TransactionListItem> {
    override fun map(from: Pair<ActivityFeedMessageWithToken, Map<String, UserProfile>>): TransactionListItem {
        val (source, profiles) = from
        val msg = source.message
        val meta = msg.metadata
        val token = source.token

        val counterparty = userIdOf(meta)?.let { profiles[it.hexEncodedString()] }
        val avatar: TransactionAvatar = when {
            counterparty != null -> TransactionAvatar.Profile(counterparty)
            hasNoCounterparty(meta) && token != null -> TransactionAvatar.TokenIcon(token)
            else -> TransactionAvatar.Generic
        }

        val prefix: String? = when {
            meta == null -> null
            meta.isOutgoing -> "-"
            else -> "+"
        }

        return TransactionListItem(
            // Full hex of the message id — NOT `id.uuid.toString()`: `ID.uuid` is null for any id
            // that isn't exactly 16 bytes (activity ids aren't), so uuid.toString() collapses EVERY
            // row to the literal key "null". Duplicate keys wedge the LazyColumn under the app's
            // SharedTransitionLayout lookahead (whole-app freeze as a duplicate-keyed row scrolls in).
            id = msg.id.hexEncodedString(),
            title = resolveTitle(resources, meta, msg.text, msg.textSubstitutions, counterparty, profiles),
            timestamp = msg.timestamp,
            avatar = avatar,
            signedAmountPrefix = prefix,
            amount = msg.amount?.nativeAmount,
            canCancel = (meta as? MessageMetadata.IndirectlySentCrypto)?.canCancel == true,
        )
    }
}

/**
 * Resolves the row title.
 *
 * When the server sends indexed placeholders ({0}, {1}, …) it fills them from [substitutions],
 * preferring the observed display name for `UserId` substitutions (server
 * [MessageSubstitution.fallback] otherwise).
 *
 * Otherwise the server sends a bare verb (e.g. "Tipped", "Received") and the client completes it
 * with the relevant subject, resolved reactively (the bare verb shows until it lands):
 * - received tips read "Tip from <name>";
 * - tips/sends and anything else with a counterparty append the **counterparty** name — "Tipped Sally".
 *
 * Buys/sells/deposits carry no counterparty, so they render the server text verbatim
 * ("Purchased", "Sold", "Added").
 */
private fun resolveTitle(
    resources: ResourceHelper,
    meta: MessageMetadata?,
    text: String,
    substitutions: List<MessageSubstitution>,
    counterparty: UserProfile?,
    profiles: Map<String, UserProfile>,
): String {
    if (substitutions.isNotEmpty()) {
        var result = text
        substitutions.forEachIndexed { index, substitution ->
            val name = when (substitution) {
                is MessageSubstitution.UserId ->
                    profiles[substitution.userId.hexEncodedString()]?.displayName?.takeIf { it.isNotBlank() }
                        ?: substitution.fallback
                is MessageSubstitution.Phone -> substitution.fallback
            }
            result = result.replace("{$index}", name)
        }
        return result
    }

    val counterpartyName = counterparty?.displayName?.takeIf { it.isNotBlank() }
    return when (meta) {
        is MessageMetadata.ReceivedCrypto ->
            if (counterpartyName != null) resources.getString(R.string.title_activity_tipFrom, counterpartyName) else text
        else ->
            if (counterpartyName != null) "$text $counterpartyName" else text
    }
}

private fun userIdOf(meta: MessageMetadata?): ID? = when (meta) {
    is MessageMetadata.DirectlySentCrypto -> meta.userId
    is MessageMetadata.ReceivedCrypto -> meta.userId
    else -> null
}

private fun hasNoCounterparty(meta: MessageMetadata?): Boolean = when (meta) {
    MessageMetadata.DepositedCrypto,
    MessageMetadata.WithdrewCrypto,
    MessageMetadata.BoughtToken,
    MessageMetadata.SoldToken -> true
    else -> false
}

/** Whether an entry debits the user (show a "-" and, if desired, a debit treatment). */
val MessageMetadata.isOutgoing: Boolean
    get() = when (this) {
        is MessageMetadata.DirectlySentCrypto,
        is MessageMetadata.IndirectlySentCrypto,
        MessageMetadata.WithdrewCrypto,
        MessageMetadata.SoldToken,
        is MessageMetadata.PaidCrypto -> true
        is MessageMetadata.ReceivedCrypto,
        MessageMetadata.DepositedCrypto,
        MessageMetadata.BoughtToken,
        MessageMetadata.Unknown -> false
    }
