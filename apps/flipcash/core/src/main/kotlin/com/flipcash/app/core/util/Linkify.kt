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
     * The handle form — `flipcash.com/sally_streamer` (node 9442:3673) — is what an account shows
     * and shares once it has claimed a username, because it reads as a person rather than as a
     * UUID; the id form stays the address for an account without one. [TipCardOwner.preferringUsername]
     * is that precedence, for callers that hold both.
     *
     * Note the handle form's bare host, no `app.` subdomain: the manifest claims `flipcash.com` for
     * username-shaped paths only, so this is the exact shape that has to resolve back into the app.
     */
    fun tipcard(owner: TipCardOwner): String = when (owner) {
        is TipCardOwner.ById -> "https://app.flipcash.com/tip/${owner.userId.uuid}"
        is TipCardOwner.ByUsername -> "https://flipcash.com/${owner.username}"
    }

    fun download(shareRef: String): String = "https://flipcash.com/download?r=${shareRef}"
    fun whatsApp(phoneNumber: String, message: String): String =
        "https://wa.me/${phoneNumber.removePrefix("+")}?text=${message.urlEncode()}"
    fun tweet(message: String): String = "https://www.twitter.com/intent/tweet?text=${message.urlEncode()}"
    fun tokenInfo(token: Token): String = tokenInfo(token.address)
    fun tokenInfo(mint: Mint): String = "https://app.flipcash.com/token/${mint.base58()}"
    fun tipChatById(chatId: ChatId): String = "https://app.flipcash.com/tip/chat/${chatId.bytes.encodeBase64(urlSafe = true)}"
}