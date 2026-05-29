package com.flipcash.app.core.util

import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.utils.urlEncode

object Linkify {
    fun cashLink(entropy: String): String = "https://send.flipcash.com/c/#/e=${entropy}"
    fun download(shareRef: String): String = "https://flipcash.com/download?r=${shareRef}"
    fun whatsApp(phoneNumber: String, message: String): String =
        "https://wa.me/${phoneNumber.removePrefix("+")}?text=${message.urlEncode()}"
    fun tweet(message: String): String = "https://www.twitter.com/intent/tweet?text=${message.urlEncode()}"
    fun tokenInfo(token: Token): String = tokenInfo(token.address)
    fun tokenInfo(mint: Mint): String = "https://app.flipcash.com/token/${mint.base58()}"
}