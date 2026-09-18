package com.flipcash.app.core.util

import com.flipcash.app.core.tipping.TipCardOwner
import com.flipcash.services.models.chat.ChatId
import com.getcode.opencode.model.core.uuid
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.utils.encodeBase64
import com.getcode.utils.hexEncodedString
import com.getcode.utils.urlEncode

object Linkify {
    fun cashLink(entropy: String): String = "https://send.flipcash.com/c/#/e=${entropy}"

    /**
     * A tip card's URL, in the form its [owner] is named by.
     *
     * One segment on the bare host either way — `flipcash.com/sally_streamer` (node 9442:3673)
     * for an account that has claimed a handle, `flipcash.com/{uuid}` for one that hasn't. The
     * handle is what an account shows and shares once it has one, because it reads as a person
     * rather than as a UUID; [TipCardOwner.preferringUsername] is that precedence, for callers
     * that hold both.
     *
     * Note the bare host, no `app.` subdomain: the manifest claims `flipcash.com` for
     * handle-shaped and UUID-shaped paths only, so these are the exact shapes that have to
     * resolve back into the app. The older `app.flipcash.com/tip/{uuid}` form is still routed —
     * links already shared carry it — but nothing writes it any more.
     */
    fun tipcard(owner: TipCardOwner): String = when (owner) {
        is TipCardOwner.ById -> "https://flipcash.com/${owner.userId.uuid}"
        is TipCardOwner.ByUsername -> "https://flipcash.com/${owner.username}"
    }

    fun download(shareRef: String): String = "https://flipcash.com/download?r=${shareRef}"
    fun whatsApp(phoneNumber: String, message: String): String =
        "https://wa.me/${phoneNumber.removePrefix("+")}?text=${message.urlEncode()}"
    fun tweet(message: String): String = "https://www.twitter.com/intent/tweet?text=${message.urlEncode()}"
    fun tokenInfo(token: Token): String = tokenInfo(token.address)
    fun tokenInfo(mint: Mint): String = "https://app.flipcash.com/token/${mint.base58()}"
    fun tipChatById(chatId: ChatId): String = "https://app.flipcash.com/tip/chat/${chatId.bytes.encodeBase64(urlSafe = true)}"

    /**
     * A group chat's invite link — `app.flipcash.com/chat/{uuid}`.
     *
     * The id is written as a dashed UUID rather than as the base64url [tipChatById] uses, because
     * `common.v1.ChatId.value` is a 16-byte UUID for a group and that is its canonical text form.
     * Nothing mints this link server-side: there is no invite RPC in flipcash2, so the link is the
     * chat id and the client builds and parses both ends of it.
     *
     * Note the `app.` host, unlike [tipcard]'s bare one. The apex shares its path space with the
     * website and can only be claimed by path *shape*, which a UUID would satisfy — an invite on
     * the apex would be indistinguishable from a tip card addressed by account id. The `app.` host
     * belongs to the app whole, so `/chat/` can be claimed there outright.
     *
     * Null for a chat that has no UUID form: a DM's id is a 32-byte hash, and no invite exists for
     * one. Callers hold a group id by construction, so this is unreachable rather than a case to
     * render.
     */
    fun groupChatInvite(chatId: ChatId): String? =
        chatId.bytes.toList().uuid?.let { "https://app.flipcash.com/chat/$it" }
}