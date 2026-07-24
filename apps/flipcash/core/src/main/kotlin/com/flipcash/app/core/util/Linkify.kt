package com.flipcash.app.core.util

import com.flipcash.services.models.chat.ChatId
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.uuid
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.utils.encodeBase64
import com.getcode.utils.hexEncodedString
import com.getcode.utils.urlEncode

object Linkify {
    fun cashLink(entropy: String): String = "https://send.flipcash.com/c/#/e=${entropy}"
    fun tipcard(userId: ID): String = "https://app.flipcash.com/tip/${userId.uuid}"
    fun download(shareRef: String): String = "https://flipcash.com/download?r=${shareRef}"
    fun whatsApp(phoneNumber: String, message: String): String =
        "https://wa.me/${phoneNumber.removePrefix("+")}?text=${message.urlEncode()}"
    fun tweet(message: String): String = "https://www.twitter.com/intent/tweet?text=${message.urlEncode()}"
    fun tokenInfo(token: Token): String = tokenInfo(token.address)
    fun tokenInfo(mint: Mint): String = "https://app.flipcash.com/token/${mint.base58()}"
    fun chatById(chatId: ChatId): String = "https://app.flipcash.com/chat/${chatId.bytes.encodeBase64(urlSafe = true)}"
    fun tipChatById(chatId: ChatId): String = "https://app.flipcash.com/tip/chat/${chatId.bytes.encodeBase64(urlSafe = true)}"
    fun chatByPhone(phoneNumber: String): String = "https://app.flipcash.com/chat/${phoneNumber.urlEncode()}"
}