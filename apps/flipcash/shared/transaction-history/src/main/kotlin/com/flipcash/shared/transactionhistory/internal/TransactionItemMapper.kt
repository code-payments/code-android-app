package com.flipcash.shared.transactionhistory.internal

import com.flipcash.app.core.feed.ActivityFeedMessageWithToken
import com.flipcash.app.core.feed.MessageMetadata
import com.flipcash.app.core.feed.MessageSubstitution
import com.flipcash.services.models.UserProfile
import com.flipcash.shared.transactionhistory.R
import com.flipcash.shared.transactionhistory.TransactionAvatar
import com.flipcash.shared.transactionhistory.TransactionListItem
import com.flipcash.shared.transactionhistory.convertOf
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
        val convert = convertOf(meta)
        val avatar: TransactionAvatar = when {
            // A face says who, not what, so the mint rides along as a badge (see [badgeToken]).
            counterparty != null -> TransactionAvatar.Profile(counterparty, badgeToken = token)
            // A convert always draws both sides, even before both tokens have resolved.
            convert != null -> TransactionAvatar.SwapTokens(from = token, to = source.toToken)
            (hasNoCounterparty(meta) || isUnidentifiedBill(meta)) && token != null ->
                TransactionAvatar.TokenIcon(token)
            // Also person-shaped — a named counterparty whose profile hasn't landed yet — so it
            // carries the badge too, and the row gains only the face when the profile resolves.
            else -> TransactionAvatar.Generic(badgeToken = token)
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
            title = convertTitle(resources, source)
                ?: resolveTitle(resources, meta, msg.text, msg.textSubstitutions, counterparty, profiles),
            timestamp = msg.timestamp,
            avatar = avatar,
            signedAmountPrefix = prefix,
            amount = msg.amount?.nativeAmount,
            fee = convert?.fee,
            canCancel = (meta as? MessageMetadata.IndirectlySentCrypto)?.canCancel == true,
        )
    }
}

/**
 * A convert spans two mints, so its row reads as the exchange itself — "USDF → Dad Cash" — rather
 * than the server's bare verb ("Converted"), which says nothing about either side.
 *
 * Returns null for anything that isn't a convert, and for a convert whose tokens haven't both
 * resolved yet: token metadata arrives reactively, so the server text shows until it lands.
 */
private fun convertTitle(
    resources: ResourceHelper,
    source: ActivityFeedMessageWithToken,
): String? {
    convertOf(source.message.metadata) ?: return null
    val from = source.token?.name?.takeIf { it.isNotBlank() } ?: return null
    val to = source.toToken?.name?.takeIf { it.isNotBlank() } ?: return null
    return resources.getString(R.string.title_activity_convert, from, to)
}

/**
 * Resolves the row title.
 *
 * When the server sends indexed placeholders ({0}, {1}, …) it fills them from [substitutions],
 * preferring the observed display name for `UserId` substitutions (server
 * [MessageSubstitution.fallback] otherwise).
 *
 * Otherwise the server sends a bare verb and the client completes it with the counterparty, resolved
 * reactively (the bare verb shows until it lands). The verb is what separates a tip from a plain
 * send — the server picks it from the payment's `ChatMetadata.TipDmPayment.Location`, so a tip-card
 * tip reads "Tipped" and an in-chat send reads "Sent". Peer payments are phrased from it:
 * - sent: "Tipped Sally" for a tip, "Sent to Sally" otherwise;
 * - received: "Tip from Sally" for a tip, "Received from Sally" otherwise.
 *
 * Anything else with a counterparty appends the name to the server verb.
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

    val counterpartyName = counterparty?.displayName?.takeIf { it.isNotBlank() } ?: return text
    val isTip = text.isTipVerb()
    return when (meta) {
        is MessageMetadata.ReceivedCrypto ->
            if (isTip) resources.getString(R.string.title_activity_tipFrom, counterpartyName)
            else resources.getString(R.string.title_activity_receivedFrom, counterpartyName)
        is MessageMetadata.DirectlySentCrypto ->
            // "Tipped" already reads as a transitive verb; "Sent" needs the preposition.
            if (isTip) "$text $counterpartyName"
            else resources.getString(R.string.title_activity_sentTo, counterpartyName)
        else -> "$text $counterpartyName"
    }
}

/**
 * Whether the server's bare verb marks this payment as a tip ("Tip", "Tipped") rather than a plain
 * send ("Sent", "Received"). The activity feed carries no structured tip flag — `activity/v1` models
 * a peer payment only as directly-sent/received crypto — so the verb is the only signal the client
 * gets. Matching it is safe while `localized_text` is English-only; a localized feed would need the
 * distinction promoted into the notification metadata.
 */
private fun String.isTipVerb(): Boolean = trim().startsWith("tip", ignoreCase = true)

private fun userIdOf(meta: MessageMetadata?): ID? = when (meta) {
    is MessageMetadata.DirectlySentCrypto -> meta.userId
    is MessageMetadata.ReceivedCrypto -> meta.userId
    else -> null
}

/**
 * Whether this is a bill hand-off — a give or a grab — that names nobody.
 *
 * The two devices never exchange identities during one: the grabber's `RequestToGrabBill` carries a
 * destination token account and nothing else, so when the server also leaves the notification's
 * identifier unset there is no counterparty to resolve, now or later. The row would otherwise keep
 * the generic silhouette forever; the token's own icon at least says what moved.
 *
 * Deliberately narrow: a peer payment whose profile simply hasn't landed yet *does* carry an
 * identifier, so it stays generic and swaps in the real avatar when the profile arrives.
 */
private fun isUnidentifiedBill(meta: MessageMetadata?): Boolean = when (meta) {
    is MessageMetadata.DirectlySentCrypto -> meta.userId == null && meta.phoneNumber == null
    is MessageMetadata.ReceivedCrypto -> meta.userId == null && meta.phoneNumber == null
    else -> false
}

private fun hasNoCounterparty(meta: MessageMetadata?): Boolean = when (meta) {
    MessageMetadata.DepositedCrypto,
    is MessageMetadata.WithdrewCrypto,
    MessageMetadata.BoughtToken,
    is MessageMetadata.SwappedCrypto,
    // A cash link is sent to whoever opens it, so it carries a gift-card vault instead of a
    // recipient — there is never a profile to draw, only the token that moved.
    is MessageMetadata.IndirectlySentCrypto,
    MessageMetadata.SoldToken -> true
    else -> false
}

/** Whether an entry debits the user (show a "-" and, if desired, a debit treatment). */
val MessageMetadata.isOutgoing: Boolean
    get() = when (this) {
        is MessageMetadata.DirectlySentCrypto,
        is MessageMetadata.IndirectlySentCrypto,
        is MessageMetadata.WithdrewCrypto,
        MessageMetadata.SoldToken,
        // A swap debits the source mint (the `from` side), so treat it as outgoing.
        is MessageMetadata.SwappedCrypto,
        is MessageMetadata.PaidCrypto -> true
        is MessageMetadata.ReceivedCrypto,
        MessageMetadata.DepositedCrypto,
        MessageMetadata.BoughtToken,
        MessageMetadata.Unknown -> false
    }
